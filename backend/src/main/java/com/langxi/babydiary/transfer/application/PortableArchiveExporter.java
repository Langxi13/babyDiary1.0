package com.langxi.babydiary.transfer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.StepUpService;
import com.langxi.babydiary.media.application.MediaAccessPolicy;
import com.langxi.babydiary.media.application.MediaRepository;
import com.langxi.babydiary.media.application.MediaVariantPolicy;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.space.application.SpaceAccess;
import com.langxi.babydiary.storage.ObjectStorageRegistry;
import com.langxi.babydiary.storage.StoredObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
class PortableArchiveExporter {
    private final SpaceAccess spaces;
    private final TransferRepository transfers;
    private final ObjectStorageRegistry storages;
    private final ObjectMapper json;
    private final StepUpService stepUp;
    private final MediaRepository media;
    private final MediaVariantPolicy variants;
    private final MediaAccessPolicy mediaAccess;

    PortableArchiveExporter(
            SpaceAccess spaces,
            TransferRepository transfers,
            ObjectStorageRegistry storages,
            ObjectMapper json,
            StepUpService stepUp,
            MediaRepository media,
            MediaVariantPolicy variants,
            MediaAccessPolicy mediaAccess) {
        this.spaces = spaces;
        this.transfers = transfers;
        this.storages = storages;
        this.json = json;
        this.stepUp = stepUp;
        this.media = media;
        this.variants = variants;
        this.mediaAccess = mediaAccess;
    }

    TemporaryDownload export(UUID spaceId, AccountPrincipal principal, String stepUpToken)
            throws IOException {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, principal.accountId());
        List<TransferRepository.DiaryData> rows =
                transfers.findDiaries(
                        space.internalId(),
                        principal.accountId(),
                        null,
                        null,
                        PortableArchiveFormat.MAX_DIARIES + 1);
        if (rows.size() > PortableArchiveFormat.MAX_DIARIES) {
            throw ApiException.badRequest("EXPORT_TOO_MANY_DIARIES", "单次最多导出2000篇日记");
        }
        if (rows.stream().anyMatch(TransferRepository.DiaryData::locked)) {
            stepUp.require(principal, stepUpToken);
        }
        PortableArchiveFormat.Manifest manifest = manifest(spaceId, space.internalId(), rows);
        long mediaCount = manifest.diaries.stream().mapToLong(diary -> diary.media.size()).sum();
        if (mediaCount + 1 > PortableArchiveFormat.MAX_ENTRIES) {
            throw ApiException.badRequest("EXPORT_TOO_MANY_MEDIA", "单次归档最多包含9999个媒体文件");
        }
        List<UUID> assetIds =
                manifest.diaries.stream()
                        .flatMap(diary -> diary.media.stream())
                        .map(item -> item.id)
                        .distinct()
                        .toList();
        if (!mediaAccess.protectedAssets(spaceId, assetIds).isEmpty()) {
            stepUp.require(principal, stepUpToken);
        }
        Path output = Files.createTempFile("baby-diary-v3-export-", ".zip");
        try (ZipOutputStream zip =
                new ZipOutputStream(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
            long written = 0;
            for (PortableArchiveFormat.Diary diary : manifest.diaries) {
                for (PortableArchiveFormat.Media item : diary.media) {
                    if (item.sizeBytes <= 0
                            || item.sizeBytes > PortableArchiveFormat.MAX_ENTRY_BYTES
                            || written + item.sizeBytes
                                    > PortableArchiveFormat.MAX_UNCOMPRESSED_BYTES) {
                        throw ApiException.badRequest("EXPORT_SIZE_LIMIT", "归档中的媒体文件超过导出限制");
                    }
                    try (StoredObject object =
                            storages.require(item.storageProvider).get(item.storageKey)) {
                        if (object.length() != item.sizeBytes) {
                            throw new IOException(
                                    "Stored media size does not match metadata: " + item.path);
                        }
                        zip.putNextEntry(new ZipEntry(item.path));
                        try {
                            object.stream().transferTo(zip);
                        } finally {
                            zip.closeEntry();
                        }
                    }
                    written += item.sizeBytes;
                    item.storageKey = null;
                    item.storageProvider = null;
                }
            }
            byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
            if (bytes.length > PortableArchiveFormat.MAX_MANIFEST_BYTES
                    || written + bytes.length > PortableArchiveFormat.MAX_UNCOMPRESSED_BYTES) {
                throw ApiException.badRequest("EXPORT_SIZE_LIMIT", "归档清单超过导出限制");
            }
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(bytes);
            zip.closeEntry();
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(output);
            throw exception;
        }
        return new TemporaryDownload(output);
    }

    private PortableArchiveFormat.Manifest manifest(
            UUID spaceId, long internalSpaceId, List<TransferRepository.DiaryData> rows) {
        List<Long> ids = rows.stream().map(TransferRepository.DiaryData::diaryId).toList();
        Map<Long, List<PortableArchiveFormat.Tag>> tagsByDiary = new HashMap<>();
        Map<Long, List<PortableArchiveFormat.Media>> mediaByDiary = new HashMap<>();
        Map<Long, List<PortableArchiveFormat.Comment>> commentsByDiary = new HashMap<>();
        Map<Long, UUID> publicIdByDiary = new HashMap<>();
        rows.forEach(
                row -> publicIdByDiary.put(row.diaryId(), BinaryUuid.fromBytes(row.publicId())));
        if (!ids.isEmpty()) {
            transfers
                    .findTags(ids)
                    .forEach(
                            row ->
                                    tagsByDiary
                                            .computeIfAbsent(
                                                    row.diaryId(), ignored -> new ArrayList<>())
                                            .add(
                                                    new PortableArchiveFormat.Tag(
                                                            row.name(), row.color())));
            List<TransferRepository.MediaData> mediaRows = transfers.findMedia(ids);
            Map<UUID, MediaAsset> assets =
                    media
                            .findByPublicIdsInSpace(
                                    internalSpaceId,
                                    mediaRows.stream()
                                            .map(row -> BinaryUuid.fromBytes(row.publicId()))
                                            .distinct()
                                            .toList())
                            .stream()
                            .collect(
                                    java.util.stream.Collectors.toMap(
                                            MediaAsset::id, value -> value));
            mediaRows.forEach(
                    row -> {
                        UUID assetId = BinaryUuid.fromBytes(row.publicId());
                        MediaAsset asset = assets.get(assetId);
                        MediaAsset.Variant original =
                                asset == null
                                        ? null
                                        : variants.select(asset.variants(), "ORIGINAL", null)
                                                .orElse(null);
                        if (original == null) {
                            throw new IllegalStateException(
                                    "Media original variant is missing: " + assetId);
                        }
                        String path =
                                "objects/"
                                        + publicIdByDiary.get(row.diaryId())
                                        + "/"
                                        + assetId
                                        + extension(row.originalFilename(), original.contentType());
                        mediaByDiary
                                .computeIfAbsent(row.diaryId(), ignored -> new ArrayList<>())
                                .add(
                                        new PortableArchiveFormat.Media(
                                                assetId,
                                                path,
                                                row.originalFilename(),
                                                row.mediaType(),
                                                original.contentType(),
                                                original.sizeBytes(),
                                                row.caption(),
                                                row.takenAt(),
                                                row.position(),
                                                original.storageProvider(),
                                                original.storageKey()));
                    });
            transfers
                    .findComments(ids)
                    .forEach(
                            row ->
                                    commentsByDiary
                                            .computeIfAbsent(
                                                    row.diaryId(), ignored -> new ArrayList<>())
                                            .add(
                                                    new PortableArchiveFormat.Comment(
                                                            row.username(),
                                                            row.content(),
                                                            row.createdAt())));
        }
        List<PortableArchiveFormat.Diary> diaries =
                rows.stream()
                        .map(
                                row ->
                                        new PortableArchiveFormat.Diary(
                                                BinaryUuid.fromBytes(row.publicId()),
                                                row.title(),
                                                row.diaryDate(),
                                                row.contentHtml(),
                                                row.moodKey(),
                                                row.visibility(),
                                                row.locked(),
                                                tagsByDiary.getOrDefault(row.diaryId(), List.of()),
                                                mediaByDiary.getOrDefault(row.diaryId(), List.of()),
                                                commentsByDiary.getOrDefault(
                                                        row.diaryId(), List.of())))
                        .toList();
        return new PortableArchiveFormat.Manifest(
                PortableArchiveFormat.VERSION,
                Instant.now(),
                spaceId,
                transfers.findSpaceName(internalSpaceId),
                diaries);
    }

    private String extension(String filename, String contentType) {
        if (filename != null) {
            int index = filename.lastIndexOf('.');
            if (index >= 0 && filename.length() - index <= 10) {
                String value = filename.substring(index).toLowerCase(Locale.ROOT);
                if (value.matches("\\.[a-z0-9]+")) return value;
            }
        }
        return switch (contentType == null ? "" : contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            case "audio/mpeg" -> ".mp3";
            case "audio/ogg" -> ".ogg";
            case "audio/wav", "audio/x-wav" -> ".wav";
            default -> ".bin";
        };
    }
}
