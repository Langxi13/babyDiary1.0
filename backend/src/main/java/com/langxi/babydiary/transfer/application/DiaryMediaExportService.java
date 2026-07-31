package com.langxi.babydiary.transfer.application;

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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class DiaryMediaExportService {
    private static final int MAX_DIARIES = 2_000;
    private static final int MAX_IMAGES = 10_000;
    private static final long MAX_IMAGE_BYTES = 256L * 1024 * 1024;
    private static final long MAX_EXPORT_BYTES = 1024L * 1024 * 1024;

    private final SpaceAccess spaces;
    private final TransferRepository transfers;
    private final MediaRepository media;
    private final MediaVariantPolicy variants;
    private final ObjectStorageRegistry storages;
    private final StepUpService stepUp;
    private final MediaAccessPolicy mediaAccess;

    public DiaryMediaExportService(
            SpaceAccess spaces,
            TransferRepository transfers,
            MediaRepository media,
            MediaVariantPolicy variants,
            ObjectStorageRegistry storages,
            StepUpService stepUp,
            MediaAccessPolicy mediaAccess) {
        this.spaces = spaces;
        this.transfers = transfers;
        this.media = media;
        this.variants = variants;
        this.storages = storages;
        this.stepUp = stepUp;
        this.mediaAccess = mediaAccess;
    }

    public TemporaryDownload export(
            UUID spaceId,
            AccountPrincipal principal,
            LocalDate startDate,
            LocalDate endDate,
            String stepUpToken)
            throws IOException {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw ApiException.badRequest("DATE_RANGE_INVALID", "请选择有效的开始和结束日期");
        }
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, principal.accountId());
        List<TransferRepository.DiaryData> diaries =
                transfers.findDiaries(
                        space.internalId(),
                        principal.accountId(),
                        startDate,
                        endDate,
                        MAX_DIARIES + 1);
        if (diaries.size() > MAX_DIARIES) {
            throw ApiException.badRequest("EXPORT_TOO_MANY_DIARIES", "单次最多导出2000篇日记的图片");
        }
        if (diaries.stream().anyMatch(TransferRepository.DiaryData::locked)) {
            stepUp.require(principal, stepUpToken);
        }

        List<Long> diaryIds = diaries.stream().map(TransferRepository.DiaryData::diaryId).toList();
        List<TransferRepository.MediaData> rows =
                diaryIds.isEmpty()
                        ? List.of()
                        : transfers.findMedia(diaryIds).stream()
                                .filter(value -> "IMAGE".equals(value.mediaType()))
                                .toList();
        if (rows.isEmpty()) {
            throw ApiException.badRequest("EXPORT_NO_IMAGES", "所选日期范围内没有可导出的图片");
        }
        if (rows.size() > MAX_IMAGES) {
            throw ApiException.badRequest("EXPORT_TOO_MANY_IMAGES", "单次最多导出10000张图片");
        }
        List<UUID> assetIds =
                rows.stream()
                        .map(value -> BinaryUuid.fromBytes(value.publicId()))
                        .distinct()
                        .toList();
        if (!mediaAccess.protectedAssets(spaceId, assetIds).isEmpty()) {
            stepUp.require(principal, stepUpToken);
        }

        Map<UUID, MediaAsset> assets =
                media.findByPublicIdsInSpace(space.internalId(), assetIds).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        MediaAsset::id,
                                        value -> value,
                                        (left, right) -> left,
                                        LinkedHashMap::new));
        Map<Long, TransferRepository.DiaryData> diaryById =
                diaries.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        TransferRepository.DiaryData::diaryId,
                                        value -> value,
                                        (left, right) -> left,
                                        LinkedHashMap::new));

        Path output = Files.createTempFile("baby-diary-images-", ".zip");
        try (ZipOutputStream zip =
                new ZipOutputStream(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
            long written = 0;
            for (TransferRepository.MediaData row : rows) {
                UUID assetId = BinaryUuid.fromBytes(row.publicId());
                MediaAsset asset = assets.get(assetId);
                MediaAsset.Variant original =
                        asset == null
                                ? null
                                : variants.select(asset.variants(), "ORIGINAL", null).orElse(null);
                if (original == null) {
                    throw new IOException("Media original variant is missing: " + assetId);
                }
                if (original.sizeBytes() <= 0
                        || original.sizeBytes() > MAX_IMAGE_BYTES
                        || written + original.sizeBytes() > MAX_EXPORT_BYTES) {
                    throw ApiException.badRequest("EXPORT_SIZE_LIMIT", "导出的图片总大小超过限制");
                }
                TransferRepository.DiaryData diary = diaryById.get(row.diaryId());
                String directory = diary.diaryDate() + "_" + BinaryUuid.fromBytes(diary.publicId());
                String filename =
                        String.format(
                                "%03d_%s_%s",
                                Math.max(0, row.position()) + 1,
                                assetId,
                                safeFilename(row.originalFilename()));
                try (StoredObject object =
                        storages.require(original.storageProvider()).get(original.storageKey())) {
                    if (object.length() != original.sizeBytes()) {
                        throw new IOException(
                                "Stored media size does not match metadata: " + assetId);
                    }
                    zip.putNextEntry(new ZipEntry(directory + "/" + filename));
                    try {
                        object.stream().transferTo(zip);
                    } finally {
                        zip.closeEntry();
                    }
                }
                written += original.sizeBytes();
            }
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(output);
            throw exception;
        }
        return new TemporaryDownload(output);
    }

    private String safeFilename(String value) {
        String filename = value == null ? "image" : value.trim();
        filename = filename.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (filename.isBlank()) filename = "image";
        return filename.length() <= 180 ? filename : filename.substring(filename.length() - 180);
    }
}
