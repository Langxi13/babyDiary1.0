package com.langxi.babydiary.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.storage.ObjectStorage;
import com.langxi.babydiary.storage.ObjectStorageRegistry;
import com.langxi.babydiary.storage.StoredObject;
import com.langxi.babydiary.storage.StoredObjectInfo;
import com.langxi.babydiary.media.domain.MediaAsset;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MediaProcessingJobHandlerTest {
  private static final UUID SPACE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
  private static final UUID ASSET_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

  @Test
  void uniqueConstraintRaceDeletesOnlyTheLosingObjectAndReleasesQuotaOnce() throws Exception {
    MediaRepository media = mock(MediaRepository.class);
    MemoryStorage storage = storage();
    when(media.findInSpace(SPACE_ID, ASSET_ID, false))
        .thenReturn(Optional.of(asset(storage.source().length)));
    when(media.hasVariant(7, "THUMBNAIL", "default")).thenReturn(false);
    when(media.reserveStorage(eq(SPACE_ID), anyLong())).thenReturn(true);
    when(media.insertVariant(org.mockito.ArgumentMatchers.any())).thenReturn(false);

    handler(media, storage).handle(payload());

    ArgumentCaptor<MediaRepository.NewVariant> variant =
        ArgumentCaptor.forClass(MediaRepository.NewVariant.class);
    verify(media).insertVariant(variant.capture());
    assertThat(variant.getValue().storageKey())
        .contains("/derived/")
        .endsWith("/thumbnail/default.jpg");
    assertThat(storage.objects()).containsOnlyKeys("source");
    verify(media).releaseStorage(SPACE_ID, variant.getValue().sizeBytes());
  }

  @Test
  void successfulDerivedVariantKeepsItsUniqueObjectAndQuotaReservation() throws Exception {
    MediaRepository media = mock(MediaRepository.class);
    MemoryStorage storage = storage();
    when(media.findInSpace(SPACE_ID, ASSET_ID, false))
        .thenReturn(Optional.of(asset(storage.source().length)));
    when(media.hasVariant(7, "THUMBNAIL", "default")).thenReturn(false);
    when(media.reserveStorage(eq(SPACE_ID), anyLong())).thenReturn(true);
    when(media.insertVariant(org.mockito.ArgumentMatchers.any())).thenReturn(true);

    handler(media, storage).handle(payload());

    ArgumentCaptor<MediaRepository.NewVariant> variant =
        ArgumentCaptor.forClass(MediaRepository.NewVariant.class);
    verify(media).insertVariant(variant.capture());
    assertThat(storage.objects()).containsKeys("source", variant.getValue().storageKey());
    verify(media, never()).releaseStorage(eq(SPACE_ID), anyLong());
  }

  private MediaProcessingJobHandler handler(MediaRepository media, MemoryStorage storage) {
    ObjectMapper json = new ObjectMapper();
    return new MediaProcessingJobHandler(
        media,
        new MediaVariantPolicy(),
        new ObjectStorageRegistry(List.of(storage), "LOCAL"),
        json,
        "ffmpeg-not-used",
        "ffprobe-not-used",
        0);
  }

  private com.fasterxml.jackson.databind.JsonNode payload() {
    return new ObjectMapper()
        .valueToTree(Map.of("spaceId", SPACE_ID.toString(), "assetId", ASSET_ID.toString()));
  }

  private MediaAsset asset(long size) {
    MediaAsset.Variant original =
        new MediaAsset.Variant(
            "ORIGINAL", "source", "LOCAL", "source", "image/png", size, null, 3, 2, null, "READY");
    return new MediaAsset(
        7,
        ASSET_ID,
        SPACE_ID,
        11,
        "IMAGE",
        "image.png",
        null,
        null,
        "LINKED",
        true,
        "READY",
        LocalDateTime.now(),
        LocalDateTime.now(),
        List.of(original));
  }

  private MemoryStorage storage() throws Exception {
    BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    assertThat(ImageIO.write(image, "png", output)).isTrue();
    return new MemoryStorage(output.toByteArray());
  }

  private static final class MemoryStorage implements ObjectStorage {
    private final Map<String, byte[]> objects = new LinkedHashMap<>();

    private MemoryStorage(byte[] source) {
      objects.put("source", source);
    }

    @Override
    public String provider() {
      return "LOCAL";
    }

    @Override
    public void put(String key, InputStream input, long size, String contentType)
        throws IOException {
      byte[] value = input.readAllBytes();
      if (value.length != size) throw new IOException("size mismatch");
      objects.put(key, value);
    }

    @Override
    public StoredObject get(String key) throws IOException {
      byte[] value = objects.get(key);
      if (value == null) throw new IOException("not found");
      return new StoredObject(new ByteArrayInputStream(value), value.length, "image/png");
    }

    @Override
    public StoredObjectInfo stat(String key) throws IOException {
      byte[] value = objects.get(key);
      if (value == null) throw new IOException("not found");
      return new StoredObjectInfo(value.length, "image/png");
    }

    @Override
    public void delete(String key) {
      objects.remove(key);
    }

    private byte[] source() {
      return objects.get("source");
    }

    private Map<String, byte[]> objects() {
      return objects;
    }
  }
}
