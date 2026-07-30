package com.langxi.babydiary.v3.media.application;

import com.langxi.babydiary.storage.ObjectStorage;
import com.langxi.babydiary.storage.StoredObject;
import com.langxi.babydiary.v3.media.domain.MediaAsset;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.space.application.SpaceAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
import java.util.UUID;

@Service
public class MediaService {
    private static final long DEFAULT_MAX_UPLOAD_BYTES = 100L * 1024 * 1024;
    private static final List<String> IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final List<String> VIDEO_TYPES = List.of("video/mp4", "video/webm", "video/quicktime");
    private static final List<String> AUDIO_TYPES = List.of("audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav", "audio/x-wav");

    private final SpaceAccess spaces;
    private final MediaRepository media;
    private final ObjectStorage storage;
    private final long maxUploadBytes;

    public MediaService(SpaceAccess spaces, MediaRepository media, ObjectStorage storage,
                        @Value("${app.v3.media.max-upload-bytes:104857600}") long maxUploadBytes) {
        this.spaces = spaces;
        this.media = media;
        this.storage = storage;
        this.maxUploadBytes = maxUploadBytes > 0 ? maxUploadBytes : DEFAULT_MAX_UPLOAD_BYTES;
    }

    @Transactional(rollbackFor = Exception.class)
    public MediaAsset upload(UUID spaceId, long accountId, MultipartFile file, String caption,
                             LocalDateTime takenAt) throws IOException {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        validate(file);
        Path temporary = Files.createTempFile("baby-diary-v3-upload-", ".tmp");
        UUID publicId = UUID.randomUUID();
        String contentType = normalizeContentType(file.getContentType());
        String mediaType = mediaType(contentType);
        String storageKey = "v3/media/" + spaceId + "/" + publicId + "/original";
        long size = 0;
        boolean stored = false;
        boolean reserved = false;
        try {
            Files.copy(file.getInputStream(), temporary, StandardCopyOption.REPLACE_EXISTING);
            size = Files.size(temporary);
            if (size <= 0 || size > maxUploadBytes) {
                throw V3Exception.badRequest("MEDIA_SIZE_INVALID", "媒体文件大小无效或超过上传限制");
            }
            if (!media.reserveStorage(space.internalId(), size)) {
                throw new V3Exception(HttpStatus.INSUFFICIENT_STORAGE, "SPACE_QUOTA_EXCEEDED", "空间存储额度不足");
            }
            reserved = true;
            try (InputStream input = Files.newInputStream(temporary)) {
                storage.put(storageKey, input, size, contentType);
                stored = true;
            }
            long assetId = media.insertAsset(new MediaRepository.NewAsset(publicId, space.internalId(), accountId,
                    mediaType, safeFilename(file.getOriginalFilename()), blankToNull(caption), takenAt,
                    "LINKED", true, "READY"));
            media.insertVariant(new MediaRepository.NewVariant(assetId, "ORIGINAL", "default", storage.provider(),
                    storageKey, contentType, size, checksum(temporary), "READY"));
            return require(space.internalId(), publicId, accountId);
        } catch (IOException | RuntimeException exception) {
            if (stored) deleteQuietly(storageKey);
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
        List<MediaAsset> rows = media.findPage(new MediaRepository.Query(space.internalId(), accountId,
                blankToNull(query.mediaType()), query.libraryOnly(), cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.id(), size + 1));
        String next = null;
        if (rows.size() > size) {
            MediaAsset last = rows.remove(rows.size() - 1);
            next = encodeCursor(last.createdAt(), last.internalId());
        }
        return new Page(rows, next);
    }

    public MediaAsset detail(UUID spaceId, UUID assetId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return require(space.internalId(), assetId, accountId);
    }

    public StoredObject openVariant(UUID spaceId, UUID assetId, String variant, String profile,
                                    long accountId) throws IOException {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        String normalized = variant == null || variant.isBlank() ? "ORIGINAL" : variant.trim().toUpperCase();
        MediaAsset.Variant value = profile == null || profile.isBlank()
                ? media.findPreferredVariant(space.internalId(), assetId, normalized, accountId)
                    .orElseThrow(() -> V3Exception.notFound("MEDIA_VARIANT_NOT_FOUND", "媒体变体不存在"))
                : media.findVariant(space.internalId(), assetId, normalized, normalizeProfile(profile), accountId)
                .orElseThrow(() -> V3Exception.notFound("MEDIA_VARIANT_NOT_FOUND", "媒体变体不存在"));
        try {
            StoredObject object = storage.get(value.storageKey());
            return object.contentType() == null
                    ? new StoredObject(object.stream(), object.length(), value.contentType())
                    : object;
        } catch (IOException exception) {
            throw new IOException("Media object is unavailable", exception);
        }
    }

    public StoredObject openSignedVariant(UUID spaceId, UUID assetId, String variant, String profile) throws IOException {
        MediaAsset.Variant value = profile == null || profile.isBlank()
                ? media.findPreferredPublicVariant(spaceId, assetId, variant)
                    .orElseThrow(() -> V3Exception.notFound("MEDIA_VARIANT_NOT_FOUND", "媒体变体不存在"))
                : media.findPublicVariant(spaceId, assetId, variant, normalizeProfile(profile))
                .orElseThrow(() -> V3Exception.notFound("MEDIA_VARIANT_NOT_FOUND", "媒体变体不存在"));
        try {
            StoredObject object = storage.get(value.storageKey());
            return object.contentType() == null
                    ? new StoredObject(object.stream(), object.length(), value.contentType())
                    : object;
        } catch (IOException exception) {
            throw new IOException("Media object is unavailable", exception);
        }
    }

    @Transactional
    public void delete(UUID spaceId, UUID assetId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        MediaAsset asset = require(space.internalId(), assetId, accountId);
        if (media.softDelete(space.internalId(), assetId, accountId, LocalDateTime.now(ZoneOffset.UTC))) {
            media.releaseStorage(space.internalId(), asset.variants().stream()
                    .filter(variant -> "ORIGINAL".equals(variant.type()) && "READY".equals(variant.status()))
                    .mapToLong(MediaAsset.Variant::sizeBytes).sum());
        }
    }

    @Transactional
    public MediaAsset update(UUID spaceId, UUID assetId, long accountId, String caption,
                             LocalDateTime takenAt, String accessScope, boolean libraryVisible) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        require(space.internalId(), assetId, accountId);
        String scope = "SPACE".equalsIgnoreCase(accessScope) ? "SPACE" : "PRIVATE";
        if (!media.updateMetadata(space.internalId(), assetId, accountId, blankToNull(caption),
                takenAt, scope, libraryVisible)) {
            throw V3Exception.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权修改");
        }
        return require(space.internalId(), assetId, accountId);
    }

    private MediaAsset require(long spaceId, UUID publicId, long accountId) {
        return media.findByPublicId(spaceId, publicId, accountId)
                .orElseThrow(() -> V3Exception.notFound("MEDIA_NOT_FOUND", "媒体不存在或无权访问"));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw V3Exception.badRequest("MEDIA_FILE_REQUIRED", "请选择媒体文件");
        if (file.getSize() > maxUploadBytes) throw V3Exception.badRequest("MEDIA_SIZE_INVALID", "媒体文件超过上传限制");
        normalizeContentType(file.getContentType());
    }

    private String normalizeContentType(String value) {
        String type = value == null ? "" : value.trim().toLowerCase();
        if (IMAGE_TYPES.contains(type) || VIDEO_TYPES.contains(type) || AUDIO_TYPES.contains(type)) return type;
        throw V3Exception.badRequest("MEDIA_TYPE_UNSUPPORTED", "暂不支持该媒体类型");
    }

    private String mediaType(String contentType) {
        if (contentType.startsWith("image/")) return "IMAGE";
        if (contentType.startsWith("video/")) return "VIDEO";
        return "AUDIO";
    }

    private String normalizeProfile(String profile) {
        String normalized = profile.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{0,31}")) {
            throw V3Exception.notFound("MEDIA_VARIANT_NOT_FOUND", "媒体变体不存在");
        }
        return normalized;
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) return null;
        String name;
        try {
            name = Path.of(value).getFileName().toString().trim();
        } catch (java.nio.file.InvalidPathException exception) {
            throw V3Exception.badRequest("MEDIA_FILENAME_INVALID", "媒体文件名无效");
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
                input.transferTo(new java.io.OutputStream() {
                    @Override public void write(int value) { digest.update((byte) value); }
                    @Override public void write(byte[] bytes, int offset, int length) { digest.update(bytes, offset, length); }
                });
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (IOException ignored) {
        }
    }

    private Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            return new Cursor(LocalDateTime.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception exception) {
            throw V3Exception.badRequest("CURSOR_INVALID", "分页游标无效");
        }
    }

    private String encodeCursor(LocalDateTime createdAt, long id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (createdAt + ":" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record Cursor(LocalDateTime createdAt, long id) {
    }

    public record Query(String mediaType, boolean libraryOnly, String cursor, int size) {
    }

    public record Page(List<MediaAsset> items, String nextCursor) {
    }
}
