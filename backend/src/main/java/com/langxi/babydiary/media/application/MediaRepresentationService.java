package com.langxi.babydiary.media.application;

import com.langxi.babydiary.media.domain.MediaAsset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class MediaRepresentationService {
  private final MediaVariantPolicy variants;
  private final MediaUrlSigner urls;
  private final MediaAccessPolicy access;

  public MediaRepresentationService(
      MediaVariantPolicy variants, MediaUrlSigner urls, MediaAccessPolicy access) {
    this.variants = variants;
    this.urls = urls;
    this.access = access;
  }

  public MediaView view(MediaAsset asset, MediaAccessContext context) {
    boolean protectedContent = access.isProtected(asset.spaceId(), asset.id());
    return view(asset, context, protectedContent);
  }

  private MediaView view(MediaAsset asset, MediaAccessContext context, boolean protectedContent) {
    boolean reveal = !protectedContent || context.elevated();
    return new MediaView(
        asset.id(),
        asset.spaceId(),
        asset.mediaType(),
        asset.originalFilename(),
        asset.caption(),
        asset.takenAt(),
        asset.accessScope(),
        asset.libraryVisible(),
        asset.status(),
        asset.createdAt(),
        protectedContent,
        new MediaView.Representations(
            representation(asset, "ORIGINAL", context, reveal),
            representation(asset, "THUMBNAIL", context, reveal),
            representation(asset, "POSTER", context, reveal),
            representation(asset, "WAVEFORM", context, reveal),
            representation(asset, "TRANSCODED", context, reveal)));
  }

  public List<MediaView> views(List<MediaAsset> assets, MediaAccessContext context) {
    return views(assets, ignored -> context);
  }

  public List<MediaView> views(
      List<MediaAsset> assets, Function<MediaAsset, MediaAccessContext> contexts) {
    if (assets == null || assets.isEmpty()) return List.of();
    Set<UUID> protectedIds =
        access.protectedAssets(
            assets.get(0).spaceId(), assets.stream().map(MediaAsset::id).toList());
    return assets.stream()
        .map(asset -> view(asset, contexts.apply(asset), protectedIds.contains(asset.id())))
        .toList();
  }

  private MediaView.Representation representation(
      MediaAsset asset, String type, MediaAccessContext context, boolean reveal) {
    MediaAsset.Variant value = variants.select(asset.variants(), type, null).orElse(null);
    if (value == null) return null;
    MediaUrlSigner.SignedUrl signed =
        reveal
            ? urls.url(asset.spaceId(), asset.id(), value.type(), value.profile(), context)
            : null;
    return new MediaView.Representation(
        value.type(),
        value.profile(),
        signed == null ? null : signed.url(),
        signed == null ? null : signed.expiresAt(),
        value.contentType(),
        value.sizeBytes(),
        value.width(),
        value.height(),
        value.durationMillis());
  }
}
