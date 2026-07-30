package com.langxi.babydiary.media.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.storage.ObjectStorageRegistry;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.BackgroundJobHandler;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StorageGcJobHandler implements BackgroundJobHandler {
  private final MediaRepository media;
  private final ObjectStorageRegistry storages;
  private final ObjectMapper json;

  public StorageGcJobHandler(
      MediaRepository media, ObjectStorageRegistry storages, ObjectMapper json) {
    this.media = media;
    this.storages = storages;
    this.json = json;
  }

  @Override
  public String type() {
    return "STORAGE_GC";
  }

  @Override
  public JsonNode handle(JsonNode payload) throws Exception {
    UUID spaceId = UUID.fromString(payload.path("spaceId").asText());
    UUID assetId = UUID.fromString(payload.path("assetId").asText());
    MediaAsset asset = media.findInSpace(spaceId, assetId, true).orElse(null);
    if (asset == null || "DELETED".equals(asset.status())) {
      return json.valueToTree(
          Map.of("assetId", assetId.toString(), "deleted", true, "alreadyDeleted", true));
    }
    if (!"DELETE_PENDING".equals(asset.status())) {
      throw new IllegalStateException("Media asset is not pending deletion");
    }
    long bytes = 0;
    for (MediaAsset.Variant variant : asset.variants()) {
      if (!"READY".equals(variant.status())) continue;
      storages.require(variant.storageProvider()).delete(variant.storageKey());
      bytes += variant.sizeBytes();
    }
    media.finalizeDeletion(asset.internalId(), spaceId, bytes, LocalDateTime.now(ZoneOffset.UTC));
    return json.valueToTree(
        Map.of("assetId", assetId.toString(), "deleted", true, "releasedBytes", bytes));
  }
}
