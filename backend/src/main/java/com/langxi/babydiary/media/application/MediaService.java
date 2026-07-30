package com.langxi.babydiary.media.application;

import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.BackgroundJobQueue;
import com.langxi.babydiary.space.application.SpaceAccess;
import com.langxi.babydiary.storage.ObjectStorage;
import com.langxi.babydiary.storage.ObjectStorageRegistry;
import com.langxi.babydiary.storage.StoredObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private final SpaceAccess spaces;
    private final MediaRepository media;
    private final ObjectStorageRegistry storages;
    private final MediaFileInspector inspector;
    private final MediaVariantPolicy variants;
    private final MediaAccessPolicy access;
    private final BackgroundJobQueue jobs;

    public MediaService(
            SpaceAccess spaces,
            MediaRepository media,
            ObjectStorageRegistry storages,
            MediaFileInspector inspector,
            MediaVariantPolicy variants,
            MediaAccessPolicy access,
            BackgroundJobQueue jobs) {
        this.spaces = spaces;
        this.media = media;
        this.storages = storages;
        this.inspector = inspector;
        this.variants = variants;
        this.access = access;
        this.jobs = jobs;
    }

    public MediaAsset upload(
            UUID spaceId, long accountId, MultipartFile file, String caption, LocalDateTime takenAt)
            throws IOException {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (file == null || file.isEmpty())
            throw ApiException.badRequest("MEDIA_FILE_REQUIRED", "请选择媒体文件");
        if (file.getSize() > MediaFileInspector.AUDIO_VIDEO_MAX_BYTES) {
            throw ApiException.badRequest("MEDIA_SIZE_INVALID", "媒体文件超过上传限制");
        }
        Path temporary = Files.createTempFile("baby-diary-v3-upload-", ".tmp");
        UUID publicId = UUID.randomUUID();
        ObjectStorage storage = storages.writer();
        String storageKey = "v3/media/" + spaceId + "/" + publicId + "/original/source";
        long assetId = 0;
        long size = 0;
        boolean reserved = false;
        boolean stored = false;
        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            MediaFileInspector.Inspection inspected =
                    inspector.inspect(temporary, file.getContentType());
            size = inspected.sizeBytes();
            if (!media.reserveStorage(space.internalId(), size)) {
                throw new ApiException(
                        HttpStatus.INSUFFICIENT_STORAGE, "SPACE_QUOTA_EXCEEDED", "空间存储额度不足");
            }
            reserved = true;
            assetId =
                    media.insertAsset(
                            new MediaRepository.NewAsset(
                                    publicId,
                                    space.internalId(),
                                    accountId,
                                    inspected.mediaType(),
                                    safeFilename(file.getOriginalFilename()),
                                    blankToNull(caption),
                                    takenAt,
                                    "LINKED",
                                    true,
                                    "UPLOADING"));
            try (InputStream input = Files.newInputStream(temporary)) {
                storage.put(storageKey, input, size, inspected.contentType());
                stored = true;
            }
            if (!media.insertVariant(
                    new MediaRepository.NewVariant(
                            assetId,
                            "ORIGINAL",
                            "source",
                            storage.provider(),
                            storageKey,
                            inspected.contentType(),
                            size,
                            checksum(temporary),
                            inspected.width(),
                            inspected.height(),
                            inspected.durationMillis(),
                            "READY"))) {
                throw new IllegalStateException("Original media variant already exists");
            }
            media.markReady(assetId);
            try {
                jobs.enqueue(
                        space.internalId(),
                        accountId,
                        "MEDIA_PROCESS",
                        "asset:" + publicId,
                        java.util.Map.of(
                                "spaceId", spaceId.toString(), "assetId", publicId.toString()),
                        5);
            } catch (RuntimeException exception) {
                log.error("Unable to enqueue media processing for asset {}", publicId, exception);
            }
            return require(space.internalId(), publicId, accountId);
        } catch (IOException | RuntimeException exception) {
            if (assetId > 0) media.failUpload(assetId, now());
            if (stored) deleteQuietly(storage, storageKey);
            if (reserved) media.releaseStorage(space.internalId(), size);
            throw exception;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Page page(UUID spaceId, long accountId, Query query) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        Cursor cursor = decodeCursor(query.cursor());
        int size = Math.max(1, Math.min(query.size(), 60));
        String mediaType = normalizeMediaType(query.mediaType());
        List<MediaAsset> rows =
                new java.util.ArrayList<>(
                        media.findPage(
                                new MediaRepository.Query(
                                        space.internalId(),
                                        accountId,
                                        mediaType,
                                        query.libraryOnly(),
                                        cursor == null ? null : cursor.createdAt(),
                                        cursor == null ? null : cursor.id(),
                                        size + 1)));
        String next = null;
        if (rows.size() > size) {
            MediaAsset last = rows.remove(rows.size() - 1);
            next = encodeCursor(last.createdAt(), last.internalId());
        }
        return new Page(rows, next);
    }

    public MediaAsset detail(UUID spaceId, UUID assetId, long accountId, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        MediaAsset asset = require(space.internalId(), assetId, accountId);
        access.require(spaceId, assetId, MediaAccessContext.direct(accountId, elevated));
        return asset;
    }

    public ResolvedVariant resolveVariant(
            UUID spaceId,
            UUID assetId,
            String variant,
            String profile,
            long accountId,
            boolean elevated) {
        spaces.requireMember(spaceId, accountId);
        MediaAccessContext context = MediaAccessContext.direct(accountId, elevated);
        access.require(spaceId, assetId, context);
        MediaAsset asset =
                media.findInSpace(spaceId, assetId, false)
                        .orElseThrow(() -> ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权访问"));
        return resolve(asset, variant, profile);
    }

    public ResolvedVariant resolveSignedVariant(
            UUID spaceId,
            UUID assetId,
            String variant,
            String profile,
            MediaAccessContext context) {
        access.require(spaceId, assetId, context);
        MediaAsset asset =
                media.findInSpace(spaceId, assetId, false)
                        .orElseThrow(() -> ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权访问"));
        return resolve(asset, variant, profile);
    }

    public StoredObject open(ResolvedVariant resolved, long offset, long length)
            throws IOException {
        if (offset < 0 || length < 0 || offset + length > resolved.variant().sizeBytes()) {
            throw new IllegalArgumentException("Invalid media byte range");
        }
        return storages.require(resolved.variant().storageProvider())
                .get(resolved.variant().storageKey(), offset, length);
    }

    @Transactional
    public void delete(UUID spaceId, UUID assetId, long accountId, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        MediaAsset asset = require(space.internalId(), assetId, accountId);
        if (asset.ownerId() != accountId) {
            throw ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权删除");
        }
        access.require(spaceId, assetId, MediaAccessContext.direct(accountId, elevated));
        MediaRepository.ReferenceCounts references = media.references(asset.internalId());
        if (references != null && references.blockingTotal() > 0) {
            throw ApiException.conflict("MEDIA_IN_USE", "媒体仍被日记、相册、纪念日、头像或 AI 提案使用");
        }
        media.removeFavorites(asset.internalId());
        if (!media.markDeletePending(space.internalId(), assetId, accountId, now())) {
            throw ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权删除");
        }
        jobs.enqueue(
                space.internalId(),
                accountId,
                "STORAGE_GC",
                "asset:" + assetId,
                java.util.Map.of("spaceId", spaceId.toString(), "assetId", assetId.toString()),
                8);
    }

    @Transactional
    public MediaAsset update(
            UUID spaceId,
            UUID assetId,
            long accountId,
            String caption,
            LocalDateTime takenAt,
            String accessScope,
            boolean libraryVisible,
            boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        MediaAsset asset = require(space.internalId(), assetId, accountId);
        if (asset.ownerId() != accountId) {
            throw ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权修改");
        }
        access.require(spaceId, assetId, MediaAccessContext.direct(accountId, elevated));
        String scope = normalizeScope(accessScope);
        if (!media.updateMetadata(
                space.internalId(),
                assetId,
                accountId,
                blankToNull(caption),
                takenAt,
                scope,
                libraryVisible)) {
            throw ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权修改");
        }
        return require(space.internalId(), assetId, accountId);
    }

    private ResolvedVariant resolve(MediaAsset asset, String variant, String profile) {
        String type = variants.normalizeType(variant);
        String selectedProfile = variants.normalizeProfile(profile);
        MediaAsset.Variant value =
                variants.select(asset.variants(), type, selectedProfile)
                        .orElseThrow(
                                () -> ApiException.notFound("MEDIA_VARIANT_NOT_FOUND", "媒体变体不存在"));
        return new ResolvedVariant(asset, value, etag(value));
    }

    private MediaAsset require(long spaceId, UUID publicId, long accountId) {
        return media.findByPublicId(spaceId, publicId, accountId)
                .orElseThrow(() -> ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权访问"));
    }

    private String normalizeScope(String value) {
        String scope =
                value == null || value.isBlank() ? "LINKED" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("LINKED", "SPACE").contains(scope)) {
            throw ApiException.badRequest("MEDIA_SCOPE_INVALID", "媒体访问范围无效");
        }
        return scope;
    }

    private String normalizeMediaType(String value) {
        if (value == null || value.isBlank()) return null;
        String type = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("IMAGE", "VIDEO", "AUDIO").contains(type)) {
            throw ApiException.badRequest("MEDIA_TYPE_INVALID", "媒体类型无效");
        }
        return type;
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) return null;
        String name;
        try {
            name = Path.of(value).getFileName().toString().trim();
        } catch (java.nio.file.InvalidPathException exception) {
            throw ApiException.badRequest("MEDIA_FILENAME_INVALID", "媒体文件名无效");
        }
        return name.isBlank() ? null : name.substring(0, Math.min(name.length(), 255));
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private byte[] checksum(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0)
                    if (read > 0) digest.update(buffer, 0, read);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String etag(MediaAsset.Variant value) {
        if (value.checksumSha256() != null) {
            return "\"" + java.util.HexFormat.of().formatHex(value.checksumSha256()) + "\"";
        }
        return "W/\""
                + value.type().toLowerCase(Locale.ROOT)
                + '-'
                + value.profile()
                + '-'
                + value.sizeBytes()
                + "\"";
    }

    private void deleteQuietly(ObjectStorage storage, String key) {
        try {
            storage.delete(key);
        } catch (IOException ignored) {
        }
    }

    private Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String decoded =
                    new String(
                            Base64.getUrlDecoder().decode(value),
                            java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            return new Cursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception exception) {
            throw ApiException.badRequest("CURSOR_INVALID", "分页游标无效");
        }
    }

    private String encodeCursor(LocalDateTime createdAt, long id) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        (createdAt + ":" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record Cursor(LocalDateTime createdAt, long id) {}

    public record Query(String mediaType, boolean libraryOnly, String cursor, int size) {}

    public record Page(List<MediaAsset> items, String nextCursor) {}

    public record ResolvedVariant(MediaAsset asset, MediaAsset.Variant variant, String etag) {}
}
