package com.langxi.babydiary.media.application;

import com.langxi.babydiary.media.domain.MediaAsset;
import java.util.HashSet;
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
                reveal ? asset.originalFilename() : null,
                reveal ? asset.caption() : null,
                reveal ? asset.takenAt() : null,
                asset.accessScope(),
                asset.libraryVisible(),
                asset.status(),
                asset.createdAt(),
                protectedContent,
                new MediaView.Representations(
                        representation(asset, "ORIGINAL", context, reveal),
                        representation(asset, "THUMBNAIL", context, reveal),
                        representation(asset, "PREVIEW", context, reveal),
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
        return contextualViews(
                assets.stream()
                        .map(asset -> new ContextualAsset(asset, contexts.apply(asset), null))
                        .toList());
    }

    public List<MediaView> contextualViews(List<ContextualAsset> assets) {
        if (assets == null || assets.isEmpty()) return List.of();
        Set<UUID> protectedIds = new HashSet<>();
        assets.stream()
                .filter(value -> value.protectedContent() == null)
                .map(ContextualAsset::asset)
                .collect(java.util.stream.Collectors.groupingBy(MediaAsset::spaceId))
                .forEach(
                        (spaceId, spaceAssets) ->
                                protectedIds.addAll(
                                        access.protectedAssets(
                                                spaceId,
                                                spaceAssets.stream()
                                                        .map(MediaAsset::id)
                                                        .toList())));
        return assets.stream()
                .map(
                        value ->
                                view(
                                        value.asset(),
                                        value.context(),
                                        value.protectedContent() == null
                                                ? protectedIds.contains(value.asset().id())
                                                : value.protectedContent()))
                .toList();
    }

    public record ContextualAsset(
            MediaAsset asset, MediaAccessContext context, Boolean protectedContent) {}

    public MediaLinkView link(
            UUID spaceId,
            UUID assetId,
            String variantType,
            String profile,
            MediaAccessContext context) {
        MediaView.Representation value =
                link(spaceId, assetId, variantType, profile, context, true);
        return new MediaLinkView(
                assetId,
                new MediaView.Representations(
                        "ORIGINAL".equals(variantType) ? value : null,
                        "THUMBNAIL".equals(variantType) ? value : null,
                        "PREVIEW".equals(variantType) ? value : null,
                        "POSTER".equals(variantType) ? value : null,
                        "WAVEFORM".equals(variantType) ? value : null,
                        "TRANSCODED".equals(variantType) ? value : null));
    }

    public MediaView.Representations links(
            UUID spaceId,
            UUID assetId,
            String originalProfile,
            String thumbnailProfile,
            String previewProfile,
            MediaAccessContext context,
            boolean reveal) {
        // Keep older cached home projections readable while they expire.
        String resolvedOriginalProfile = originalProfile == null ? "source" : originalProfile;
        return new MediaView.Representations(
                link(spaceId, assetId, "ORIGINAL", resolvedOriginalProfile, context, reveal),
                link(spaceId, assetId, "THUMBNAIL", thumbnailProfile, context, reveal),
                link(spaceId, assetId, "PREVIEW", previewProfile, context, reveal),
                null,
                null,
                null);
    }

    private MediaView.Representation link(
            UUID spaceId,
            UUID assetId,
            String variantType,
            String profile,
            MediaAccessContext context,
            boolean reveal) {
        if (profile == null) return null;
        MediaUrlSigner.SignedUrl signed =
                reveal ? urls.url(spaceId, assetId, variantType, profile, context) : null;
        return new MediaView.Representation(
                variantType,
                profile,
                signed == null ? null : signed.url(),
                signed == null ? null : signed.expiresAt(),
                null,
                null,
                null,
                null,
                null);
    }

    private MediaView.Representation representation(
            MediaAsset asset, String type, MediaAccessContext context, boolean reveal) {
        MediaAsset.Variant value = variants.select(asset.variants(), type, null).orElse(null);
        if (value == null) return null;
        MediaUrlSigner.SignedUrl signed =
                reveal
                        ? urls.url(
                                asset.spaceId(), asset.id(), value.type(), value.profile(), context)
                        : null;
        return new MediaView.Representation(
                value.type(),
                value.profile(),
                signed == null ? null : signed.url(),
                signed == null ? null : signed.expiresAt(),
                reveal ? value.contentType() : null,
                reveal ? value.sizeBytes() : null,
                reveal ? value.width() : null,
                reveal ? value.height() : null,
                reveal ? value.durationMillis() : null);
    }
}
