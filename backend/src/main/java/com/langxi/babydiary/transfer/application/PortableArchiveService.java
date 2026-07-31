package com.langxi.babydiary.transfer.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.diary.application.DiaryInteractionService;
import com.langxi.babydiary.diary.application.DiaryService;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.StepUpService;
import com.langxi.babydiary.media.application.MediaService;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.space.application.SpaceAccess;
import com.langxi.babydiary.storage.ObjectStorageRegistry;
import com.langxi.babydiary.tag.application.TagService;
import com.langxi.babydiary.tag.domain.Tag;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PortableArchiveService {
    private final SpaceAccess spaces;
    private final TransferRepository mapper;
    private final ObjectStorageRegistry storages;
    private final ObjectMapper json;
    private final StepUpService stepUp;
    private final MediaService media;
    private final DiaryService diaries;
    private final DiaryInteractionService interactions;
    private final TagService tags;
    private final PortableArchiveExporter exporter;

    public PortableArchiveService(
            SpaceAccess spaces,
            TransferRepository mapper,
            ObjectStorageRegistry storages,
            ObjectMapper json,
            StepUpService stepUp,
            MediaService media,
            DiaryService diaries,
            DiaryInteractionService interactions,
            TagService tags,
            PortableArchiveExporter exporter) {
        this.spaces = spaces;
        this.mapper = mapper;
        this.storages = storages;
        this.json = json;
        this.stepUp = stepUp;
        this.media = media;
        this.diaries = diaries;
        this.interactions = interactions;
        this.tags = tags;
        this.exporter = exporter;
    }

    public TemporaryDownload exportSpace(
            UUID spaceId, AccountPrincipal principal, String stepUpToken) throws IOException {
        return exporter.export(spaceId, principal, stepUpToken);
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importSpace(
            UUID spaceId, AccountPrincipal principal, MultipartFile archive, String stepUpToken)
            throws IOException {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, principal.accountId());
        if ("SHARED".equals(space.type())
                && !("OWNER".equals(space.role()) || "ADMIN".equals(space.role()))) {
            throw ApiException.forbidden("SPACE_IMPORT_FORBIDDEN", "只有空间管理员可以批量导入日记");
        }
        if (archive == null || archive.isEmpty()) {
            throw ApiException.badRequest("ARCHIVE_REQUIRED", "请选择要导入的归档文件");
        }
        List<VariantLocation> uploadedKeys = new ArrayList<>();
        try (ArchiveContents contents = readArchive(archive)) {
            PortableArchiveFormat.Manifest manifest = parseAndValidate(contents);
            if (manifest.diaries.stream().anyMatch(value -> value.locked)) {
                stepUp.require(principal, stepUpToken);
            }
            Map<String, Tag> availableTags = new LinkedHashMap<>();
            tags.list(spaceId, principal.accountId())
                    .forEach(value -> availableTags.put(value.name(), value));
            int importedDiaries = 0;
            int importedMedia = 0;
            int skippedDiaries = 0;

            for (PortableArchiveFormat.Diary record : manifest.diaries) {
                UUID diaryId = importedDiaryId(spaceId, space.internalId(), record.id);
                if (diaryId == null) {
                    skippedDiaries++;
                    continue;
                }
                List<UUID> mediaIds = new ArrayList<>();
                for (PortableArchiveFormat.Media item : record.media) {
                    Path path = contents.entries.get(item.path);
                    MediaAsset uploaded =
                            media.upload(
                                    spaceId,
                                    principal.accountId(),
                                    new PathUpload(item.originalFilename, item.contentType, path),
                                    item.caption,
                                    item.takenAt);
                    mediaIds.add(uploaded.id());
                    uploaded.variants().stream()
                            .filter(value -> "ORIGINAL".equals(value.type()))
                            .map(
                                    value ->
                                            new VariantLocation(
                                                    value.storageProvider(), value.storageKey()))
                            .forEach(uploadedKeys::add);
                    importedMedia++;
                }
                List<UUID> tagIds = new ArrayList<>();
                for (PortableArchiveFormat.Tag item : record.tags) {
                    Tag tag = availableTags.get(item.name);
                    if (tag == null) {
                        tag = tags.create(spaceId, principal.accountId(), item.name, item.color);
                        availableTags.put(tag.name(), tag);
                    }
                    tagIds.add(tag.id());
                }
                diaries.create(
                        spaceId,
                        principal.accountId(),
                        new DiaryService.Command(
                                diaryId,
                                record.title,
                                record.diaryDate,
                                record.contentHtml,
                                record.mood,
                                record.visibility,
                                record.locked,
                                tagIds,
                                mediaIds));
                for (PortableArchiveFormat.Comment item : record.comments) {
                    String author =
                            item.author == null || item.author.isBlank()
                                    ? "原成员"
                                    : item.author.trim();
                    String content = "[" + author + "] " + item.content;
                    interactions.addComment(
                            spaceId,
                            diaryId,
                            principal.accountId(),
                            content.substring(0, Math.min(content.length(), 2000)),
                            true);
                }
                importedDiaries++;
            }
            return new ImportResult(importedDiaries, importedMedia, skippedDiaries);
        } catch (IOException | RuntimeException exception) {
            for (VariantLocation value : uploadedKeys) {
                try {
                    storages.require(value.provider()).delete(value.key());
                } catch (IOException ignored) {
                }
            }
            throw exception;
        }
    }

    private PortableArchiveFormat.Manifest parseAndValidate(ArchiveContents contents)
            throws IOException {
        Path manifestPath = contents.entries.get("manifest.json");
        if (manifestPath == null)
            throw ApiException.badRequest("ARCHIVE_MANIFEST_MISSING", "归档缺少manifest.json");
        if (Files.size(manifestPath) > PortableArchiveFormat.MAX_MANIFEST_BYTES) {
            throw ApiException.badRequest("ARCHIVE_MANIFEST_TOO_LARGE", "归档清单过大");
        }
        PortableArchiveFormat.Manifest manifest;
        try {
            manifest =
                    json.readerFor(PortableArchiveFormat.Manifest.class)
                            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .readValue(manifestPath.toFile());
        } catch (IOException exception) {
            throw ApiException.badRequest("ARCHIVE_MANIFEST_INVALID", "归档清单格式无效");
        }
        if (manifest.version != PortableArchiveFormat.VERSION) {
            throw ApiException.badRequest("ARCHIVE_VERSION_UNSUPPORTED", "仅支持V3归档文件");
        }
        if (manifest.diaries == null
                || manifest.diaries.size() > PortableArchiveFormat.MAX_DIARIES) {
            throw ApiException.badRequest("ARCHIVE_DIARY_LIMIT", "归档日记数量无效或超过2000篇");
        }
        Set<UUID> diaryIds = new HashSet<>();
        Set<String> referencedPaths = new HashSet<>();
        for (PortableArchiveFormat.Diary diary : manifest.diaries) {
            validateDiary(diary, diaryIds);
            for (PortableArchiveFormat.Media item : diary.media) {
                if (item == null
                        || item.id == null
                        || item.path == null
                        || item.contentType == null
                        || !referencedPaths.add(item.path)) {
                    throw ApiException.badRequest("ARCHIVE_MEDIA_INVALID", "归档媒体清单无效");
                }
                if (!allowedMediaType(item.contentType) || !item.path.startsWith("objects/")) {
                    throw ApiException.badRequest("ARCHIVE_MEDIA_TYPE_INVALID", "归档包含不支持的媒体类型");
                }
                Path path = contents.entries.get(item.path);
                if (path == null
                        || Files.size(path) <= 0
                        || Files.size(path) > PortableArchiveFormat.MAX_MEDIA_BYTES
                        || Files.size(path) != item.sizeBytes) {
                    throw ApiException.badRequest("ARCHIVE_MEDIA_MISSING", "归档媒体缺失或大小不一致");
                }
            }
        }
        Set<String> expectedPaths = new HashSet<>(referencedPaths);
        expectedPaths.add("manifest.json");
        if (!expectedPaths.equals(contents.entries.keySet())) {
            throw ApiException.badRequest("ARCHIVE_ENTRY_UNREFERENCED", "归档包含清单未引用的文件");
        }
        return manifest;
    }

    private void validateDiary(PortableArchiveFormat.Diary diary, Set<UUID> ids) {
        if (diary == null
                || diary.id == null
                || !ids.add(diary.id)
                || diary.diaryDate == null
                || diary.title == null
                || diary.title.isBlank()
                || diary.title.length() > 255
                || diary.contentHtml == null
                || diary.contentHtml.length() > 1_000_000
                || !("PRIVATE".equals(diary.visibility) || "SHARED".equals(diary.visibility))) {
            throw ApiException.badRequest("ARCHIVE_DIARY_INVALID", "归档包含无效日记");
        }
        diary.tags = diary.tags == null ? new ArrayList<>() : diary.tags;
        diary.media = diary.media == null ? new ArrayList<>() : diary.media;
        diary.comments = diary.comments == null ? new ArrayList<>() : diary.comments;
        if (diary.tags.size() > 50 || diary.media.size() > 50 || diary.comments.size() > 5_000) {
            throw ApiException.badRequest("ARCHIVE_REFERENCE_LIMIT", "归档中的关联数据过多");
        }
        Set<String> names = new LinkedHashSet<>();
        for (PortableArchiveFormat.Tag tag : diary.tags) {
            if (tag == null
                    || tag.name == null
                    || tag.name.isBlank()
                    || tag.name.length() > 32
                    || !names.add(tag.name)
                    || (tag.color != null && !tag.color.matches("#[0-9A-Fa-f]{6}"))) {
                throw ApiException.badRequest("ARCHIVE_TAG_INVALID", "归档包含无效标签");
            }
        }
        for (PortableArchiveFormat.Comment comment : diary.comments) {
            if (comment == null || comment.content == null || comment.content.isBlank()) {
                throw ApiException.badRequest("ARCHIVE_COMMENT_INVALID", "归档包含无效评论");
            }
        }
    }

    private ArchiveContents readArchive(MultipartFile archive) throws IOException {
        Path root = Files.createTempDirectory("baby-diary-v3-import-");
        Map<String, Path> entries = new LinkedHashMap<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip =
                new ZipInputStream(archive.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (++count > PortableArchiveFormat.MAX_ENTRIES) {
                    throw ApiException.badRequest("ARCHIVE_ENTRY_LIMIT", "归档文件数量过多");
                }
                String name = safePath(entry.getName());
                if (entries.containsKey(name)) {
                    throw ApiException.badRequest("ARCHIVE_DUPLICATE_PATH", "归档包含重复路径");
                }
                Path output = root.resolve(name).normalize();
                if (!output.startsWith(root)) throw invalidPath();
                Files.createDirectories(output.getParent());
                long entryBytes = 0;
                byte[] buffer = new byte[16 * 1024];
                try (OutputStream stream = Files.newOutputStream(output)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        if (read == 0) continue;
                        entryBytes += read;
                        total += read;
                        if (entryBytes > PortableArchiveFormat.MAX_ENTRY_BYTES
                                || total > PortableArchiveFormat.MAX_UNCOMPRESSED_BYTES) {
                            throw new ApiException(
                                    HttpStatus.PAYLOAD_TOO_LARGE,
                                    "ARCHIVE_SIZE_LIMIT",
                                    "归档解压后超过大小限制");
                        }
                        stream.write(buffer, 0, read);
                    }
                }
                entries.put(name, output);
            }
            return new ArchiveContents(root, entries);
        } catch (IOException | RuntimeException exception) {
            deleteDirectory(root);
            throw exception;
        }
    }

    private String safePath(String value) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) throw invalidPath();
        String portable = value.replace('\\', '/');
        Path path;
        try {
            path = Path.of(portable).normalize();
        } catch (RuntimeException exception) {
            throw invalidPath();
        }
        if (path.isAbsolute() || path.startsWith("..") || portable.matches("^[A-Za-z]:.*"))
            throw invalidPath();
        return path.toString().replace('\\', '/');
    }

    private UUID importedDiaryId(UUID targetSpaceId, long internalSpaceId, UUID sourceId) {
        if (mapper.diaryExists(internalSpaceId, BinaryUuid.toBytes(sourceId))) return null;
        UUID mapped =
                UUID.nameUUIDFromBytes(
                        ("baby-diary-v3-import:" + targetSpaceId + ":" + sourceId)
                                .getBytes(StandardCharsets.UTF_8));
        return mapper.diaryExists(internalSpaceId, BinaryUuid.toBytes(mapped)) ? null : mapped;
    }

    private boolean allowedMediaType(String value) {
        return Set.of(
                        "image/jpeg",
                        "image/png",
                        "image/gif",
                        "image/webp",
                        "video/mp4",
                        "video/webm",
                        "video/quicktime",
                        "audio/mpeg",
                        "audio/mp4",
                        "audio/ogg",
                        "audio/wav",
                        "audio/x-wav")
                .contains(value);
    }

    private ApiException invalidPath() {
        return ApiException.badRequest("ARCHIVE_PATH_INVALID", "归档包含非法路径");
    }

    private static void deleteDirectory(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
        } catch (IOException ignored) {
        }
    }

    private static final class ArchiveContents implements AutoCloseable {
        private final Path root;
        private final Map<String, Path> entries;

        private ArchiveContents(Path root, Map<String, Path> entries) {
            this.root = root;
            this.entries = entries;
        }

        @Override
        public void close() {
            deleteDirectory(root);
        }
    }

    public record ImportResult(int importedDiaries, int importedMedia, int skippedDiaries) {}

    private record VariantLocation(String provider, String key) {}
}
