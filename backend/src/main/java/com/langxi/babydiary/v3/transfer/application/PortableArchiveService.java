package com.langxi.babydiary.v3.transfer.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.storage.ObjectStorage;
import com.langxi.babydiary.storage.StoredObject;
import com.langxi.babydiary.v3.diary.application.DiaryInteractionService;
import com.langxi.babydiary.v3.diary.application.DiaryService;
import com.langxi.babydiary.v3.identity.application.StepUpService;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.media.application.MediaService;
import com.langxi.babydiary.v3.media.domain.MediaAsset;
import com.langxi.babydiary.v3.platform.application.BinaryUuid;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.space.application.SpaceAccess;
import com.langxi.babydiary.v3.tag.application.TagService;
import com.langxi.babydiary.v3.tag.domain.Tag;
import com.langxi.babydiary.v3.transfer.infrastructure.TransferMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class PortableArchiveService {
    static final int ARCHIVE_VERSION = 3;
    static final int MAX_DIARIES = 2_000;
    static final int MAX_ARCHIVE_ENTRIES = 10_000;
    static final long MAX_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024;
    static final long MAX_ENTRY_BYTES = 256L * 1024 * 1024;
    static final long MAX_MEDIA_BYTES = 100L * 1024 * 1024;
    static final long MAX_MANIFEST_BYTES = 5L * 1024 * 1024;

    private final SpaceAccess spaces;
    private final TransferMapper mapper;
    private final ObjectStorage storage;
    private final ObjectMapper json;
    private final StepUpService stepUp;
    private final MediaService media;
    private final DiaryService diaries;
    private final DiaryInteractionService interactions;
    private final TagService tags;

    public PortableArchiveService(SpaceAccess spaces, TransferMapper mapper, ObjectStorage storage,
                                  ObjectMapper json, StepUpService stepUp, MediaService media,
                                  DiaryService diaries, DiaryInteractionService interactions, TagService tags) {
        this.spaces = spaces;
        this.mapper = mapper;
        this.storage = storage;
        this.json = json;
        this.stepUp = stepUp;
        this.media = media;
        this.diaries = diaries;
        this.interactions = interactions;
        this.tags = tags;
    }

    public TemporaryDownload exportSpace(UUID spaceId, V3Principal principal, String stepUpToken) throws IOException {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, principal.accountId());
        List<TransferMapper.DiaryRow> rows = mapper.findDiaries(space.internalId(), principal.accountId(),
                null, null, MAX_DIARIES + 1);
        if (rows.size() > MAX_DIARIES) {
            throw V3Exception.badRequest("EXPORT_TOO_MANY_DIARIES", "单次最多导出2000篇日记");
        }
        requireStepUpForLocked(rows, principal, stepUpToken);
        ArchiveManifest manifest = manifest(spaceId, space.internalId(), principal.accountId(), rows);
        Path output = Files.createTempFile("baby-diary-v3-export-", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
            long written = 0;
            for (ArchiveDiary diary : manifest.diaries) {
                for (ArchiveMedia item : diary.media) {
                    if (item.sizeBytes <= 0 || item.sizeBytes > MAX_ENTRY_BYTES
                            || written + item.sizeBytes > MAX_UNCOMPRESSED_BYTES) {
                        throw V3Exception.badRequest("EXPORT_SIZE_LIMIT", "归档中的媒体文件超过导出限制");
                    }
                    try (StoredObject object = storage.get(item.storageKey)) {
                        if (object.length() != item.sizeBytes) {
                            throw new IOException("Stored media size does not match metadata: " + item.path);
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
                }
            }
            byte[] bytes = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
            if (bytes.length > MAX_MANIFEST_BYTES || written + bytes.length > MAX_UNCOMPRESSED_BYTES) {
                throw V3Exception.badRequest("EXPORT_SIZE_LIMIT", "归档清单超过导出限制");
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

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importSpace(UUID spaceId, V3Principal principal, MultipartFile archive,
                                    String stepUpToken) throws IOException {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, principal.accountId());
        if ("SHARED".equals(space.type())
                && !("OWNER".equals(space.role()) || "ADMIN".equals(space.role()))) {
            throw V3Exception.forbidden("SPACE_IMPORT_FORBIDDEN", "只有空间管理员可以批量导入日记");
        }
        if (archive == null || archive.isEmpty()) {
            throw V3Exception.badRequest("ARCHIVE_REQUIRED", "请选择要导入的归档文件");
        }
        List<String> uploadedKeys = new ArrayList<>();
        try (ArchiveContents contents = readArchive(archive)) {
            ArchiveManifest manifest = parseAndValidate(contents);
            if (manifest.diaries.stream().anyMatch(value -> value.locked)) {
                stepUp.require(principal, stepUpToken);
            }
            Map<String, Tag> availableTags = new LinkedHashMap<>();
            tags.list(spaceId, principal.accountId()).forEach(value -> availableTags.put(value.name(), value));
            int importedDiaries = 0;
            int importedMedia = 0;
            int skippedDiaries = 0;

            for (ArchiveDiary record : manifest.diaries) {
                UUID diaryId = importedDiaryId(spaceId, space.internalId(), record.id);
                if (diaryId == null) {
                    skippedDiaries++;
                    continue;
                }
                List<UUID> mediaIds = new ArrayList<>();
                for (ArchiveMedia item : record.media) {
                    Path path = contents.entries.get(item.path);
                    MediaAsset uploaded = media.upload(spaceId, principal.accountId(),
                            new PathUpload(item.originalFilename, item.contentType, path), item.caption, item.takenAt);
                    mediaIds.add(uploaded.id());
                    uploaded.variants().stream().filter(value -> "ORIGINAL".equals(value.type()))
                            .map(MediaAsset.Variant::storageKey).forEach(uploadedKeys::add);
                    importedMedia++;
                }
                List<UUID> tagIds = new ArrayList<>();
                for (ArchiveTag item : record.tags) {
                    Tag tag = availableTags.get(item.name);
                    if (tag == null) {
                        tag = tags.create(spaceId, principal.accountId(), item.name, item.color);
                        availableTags.put(tag.name(), tag);
                    }
                    tagIds.add(tag.id());
                }
                diaries.create(spaceId, principal.accountId(), new DiaryService.Command(diaryId, record.title,
                        record.diaryDate, record.contentHtml, record.mood, record.visibility, record.locked,
                        tagIds, mediaIds));
                for (ArchiveComment item : record.comments) {
                    String author = item.author == null || item.author.isBlank() ? "原成员" : item.author.trim();
                    String content = "[" + author + "] " + item.content;
                    interactions.addComment(spaceId, diaryId, principal.accountId(),
                            content.substring(0, Math.min(content.length(), 2000)));
                }
                importedDiaries++;
            }
            return new ImportResult(importedDiaries, importedMedia, skippedDiaries);
        } catch (IOException | RuntimeException exception) {
            for (String key : uploadedKeys) {
                try { storage.delete(key); } catch (IOException ignored) { }
            }
            throw exception;
        }
    }

    private ArchiveManifest manifest(UUID spaceId, long internalSpaceId, long accountId,
                                     List<TransferMapper.DiaryRow> rows) {
        List<Long> ids = rows.stream().map(TransferMapper.DiaryRow::diaryId).toList();
        Map<Long, List<ArchiveTag>> tagsByDiary = new HashMap<>();
        Map<Long, List<ArchiveMedia>> mediaByDiary = new HashMap<>();
        Map<Long, List<ArchiveComment>> commentsByDiary = new HashMap<>();
        Map<Long, UUID> publicIdByDiary = new HashMap<>();
        rows.forEach(row -> publicIdByDiary.put(row.diaryId(), BinaryUuid.fromBytes(row.publicId())));
        if (!ids.isEmpty()) {
            mapper.findTags(ids).forEach(row -> tagsByDiary.computeIfAbsent(row.diaryId(), ignored -> new ArrayList<>())
                    .add(new ArchiveTag(row.name(), row.color())));
            mapper.findMedia(ids).forEach(row -> {
                UUID assetId = BinaryUuid.fromBytes(row.publicId());
                String path = "objects/" + publicIdByDiary.get(row.diaryId())
                        + "/" + assetId + extension(row.originalFilename(), row.contentType());
                mediaByDiary.computeIfAbsent(row.diaryId(), ignored -> new ArrayList<>()).add(new ArchiveMedia(
                        assetId, path, row.originalFilename(), row.mediaType(), row.contentType(), row.sizeBytes(),
                        row.caption(), row.takenAt(), row.position(), row.storageKey()));
            });
            mapper.findComments(ids).forEach(row -> commentsByDiary.computeIfAbsent(row.diaryId(), ignored -> new ArrayList<>())
                    .add(new ArchiveComment(row.username(), row.content(), row.createdAt())));
        }
        List<ArchiveDiary> values = rows.stream().map(row -> new ArchiveDiary(BinaryUuid.fromBytes(row.publicId()),
                row.title(), row.diaryDate(), row.contentHtml(), row.moodKey(), row.visibility(), row.locked(),
                tagsByDiary.getOrDefault(row.diaryId(), List.of()),
                mediaByDiary.getOrDefault(row.diaryId(), List.of()),
                commentsByDiary.getOrDefault(row.diaryId(), List.of()))).toList();
        return new ArchiveManifest(ARCHIVE_VERSION, Instant.now(), spaceId, mapper.findSpaceName(internalSpaceId), values);
    }

    private ArchiveManifest parseAndValidate(ArchiveContents contents) throws IOException {
        Path manifestPath = contents.entries.get("manifest.json");
        if (manifestPath == null) throw V3Exception.badRequest("ARCHIVE_MANIFEST_MISSING", "归档缺少manifest.json");
        if (Files.size(manifestPath) > MAX_MANIFEST_BYTES) {
            throw V3Exception.badRequest("ARCHIVE_MANIFEST_TOO_LARGE", "归档清单过大");
        }
        ArchiveManifest manifest;
        try {
            manifest = json.readerFor(ArchiveManifest.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(manifestPath.toFile());
        } catch (IOException exception) {
            throw V3Exception.badRequest("ARCHIVE_MANIFEST_INVALID", "归档清单格式无效");
        }
        if (manifest.version != ARCHIVE_VERSION) {
            throw V3Exception.badRequest("ARCHIVE_VERSION_UNSUPPORTED", "仅支持V3归档文件");
        }
        if (manifest.diaries == null || manifest.diaries.size() > MAX_DIARIES) {
            throw V3Exception.badRequest("ARCHIVE_DIARY_LIMIT", "归档日记数量无效或超过2000篇");
        }
        Set<UUID> diaryIds = new HashSet<>();
        Set<String> referencedPaths = new HashSet<>();
        for (ArchiveDiary diary : manifest.diaries) {
            validateDiary(diary, diaryIds);
            for (ArchiveMedia item : diary.media) {
                if (item == null || item.id == null || item.path == null || item.contentType == null
                        || !referencedPaths.add(item.path)) {
                    throw V3Exception.badRequest("ARCHIVE_MEDIA_INVALID", "归档媒体清单无效");
                }
                if (!allowedMediaType(item.contentType) || !item.path.startsWith("objects/")) {
                    throw V3Exception.badRequest("ARCHIVE_MEDIA_TYPE_INVALID", "归档包含不支持的媒体类型");
                }
                Path path = contents.entries.get(item.path);
                if (path == null || Files.size(path) <= 0 || Files.size(path) > MAX_MEDIA_BYTES
                        || Files.size(path) != item.sizeBytes) {
                    throw V3Exception.badRequest("ARCHIVE_MEDIA_MISSING", "归档媒体缺失或大小不一致");
                }
            }
        }
        Set<String> expectedPaths = new HashSet<>(referencedPaths);
        expectedPaths.add("manifest.json");
        if (!expectedPaths.equals(contents.entries.keySet())) {
            throw V3Exception.badRequest("ARCHIVE_ENTRY_UNREFERENCED", "归档包含清单未引用的文件");
        }
        return manifest;
    }

    private void validateDiary(ArchiveDiary diary, Set<UUID> ids) {
        if (diary == null || diary.id == null || !ids.add(diary.id) || diary.diaryDate == null
                || diary.title == null || diary.title.isBlank() || diary.title.length() > 255
                || diary.contentHtml == null || diary.contentHtml.length() > 1_000_000
                || !("PRIVATE".equals(diary.visibility) || "SHARED".equals(diary.visibility))) {
            throw V3Exception.badRequest("ARCHIVE_DIARY_INVALID", "归档包含无效日记");
        }
        diary.tags = diary.tags == null ? new ArrayList<>() : diary.tags;
        diary.media = diary.media == null ? new ArrayList<>() : diary.media;
        diary.comments = diary.comments == null ? new ArrayList<>() : diary.comments;
        if (diary.tags.size() > 50 || diary.media.size() > 50 || diary.comments.size() > 5_000) {
            throw V3Exception.badRequest("ARCHIVE_REFERENCE_LIMIT", "归档中的关联数据过多");
        }
        Set<String> names = new LinkedHashSet<>();
        for (ArchiveTag tag : diary.tags) {
            if (tag == null || tag.name == null || tag.name.isBlank() || tag.name.length() > 32
                    || !names.add(tag.name) || (tag.color != null && !tag.color.matches("#[0-9A-Fa-f]{6}"))) {
                throw V3Exception.badRequest("ARCHIVE_TAG_INVALID", "归档包含无效标签");
            }
        }
        for (ArchiveComment comment : diary.comments) {
            if (comment == null || comment.content == null || comment.content.isBlank()) {
                throw V3Exception.badRequest("ARCHIVE_COMMENT_INVALID", "归档包含无效评论");
            }
        }
    }

    private ArchiveContents readArchive(MultipartFile archive) throws IOException {
        Path root = Files.createTempDirectory("baby-diary-v3-import-");
        Map<String, Path> entries = new LinkedHashMap<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(archive.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (++count > MAX_ARCHIVE_ENTRIES) {
                    throw V3Exception.badRequest("ARCHIVE_ENTRY_LIMIT", "归档文件数量过多");
                }
                String name = safePath(entry.getName());
                if (entries.containsKey(name)) {
                    throw V3Exception.badRequest("ARCHIVE_DUPLICATE_PATH", "归档包含重复路径");
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
                        if (entryBytes > MAX_ENTRY_BYTES || total > MAX_UNCOMPRESSED_BYTES) {
                            throw new V3Exception(HttpStatus.PAYLOAD_TOO_LARGE, "ARCHIVE_SIZE_LIMIT",
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
        try { path = Path.of(portable).normalize(); } catch (RuntimeException exception) { throw invalidPath(); }
        if (path.isAbsolute() || path.startsWith("..") || portable.matches("^[A-Za-z]:.*")) throw invalidPath();
        return path.toString().replace('\\', '/');
    }

    private UUID importedDiaryId(UUID targetSpaceId, long internalSpaceId, UUID sourceId) {
        if (mapper.countDiary(internalSpaceId, BinaryUuid.toBytes(sourceId)) > 0) return null;
        UUID mapped = UUID.nameUUIDFromBytes(("baby-diary-v3-import:" + targetSpaceId + ":" + sourceId)
                .getBytes(StandardCharsets.UTF_8));
        return mapper.countDiary(internalSpaceId, BinaryUuid.toBytes(mapped)) > 0 ? null : mapped;
    }

    private void requireStepUpForLocked(List<TransferMapper.DiaryRow> rows, V3Principal principal, String token) {
        if (rows.stream().anyMatch(TransferMapper.DiaryRow::locked)) stepUp.require(principal, token);
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
            case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/gif" -> ".gif";
            case "image/webp" -> ".webp"; case "video/mp4" -> ".mp4"; case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov"; case "audio/mpeg" -> ".mp3"; case "audio/ogg" -> ".ogg";
            case "audio/wav", "audio/x-wav" -> ".wav"; default -> ".bin";
        };
    }

    private boolean allowedMediaType(String value) {
        return Set.of("image/jpeg", "image/png", "image/gif", "image/webp",
                "video/mp4", "video/webm", "video/quicktime",
                "audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav", "audio/x-wav").contains(value);
    }

    private V3Exception invalidPath() {
        return V3Exception.badRequest("ARCHIVE_PATH_INVALID", "归档包含非法路径");
    }

    private static void deleteDirectory(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private static final class ArchiveContents implements AutoCloseable {
        private final Path root;
        private final Map<String, Path> entries;
        private ArchiveContents(Path root, Map<String, Path> entries) { this.root = root; this.entries = entries; }
        @Override public void close() { deleteDirectory(root); }
    }

    public record ImportResult(int importedDiaries, int importedMedia, int skippedDiaries) {}

    public static final class ArchiveManifest {
        public int version; public Instant exportedAt; public UUID sourceSpaceId; public String spaceName;
        public List<ArchiveDiary> diaries;
        public ArchiveManifest() {}
        ArchiveManifest(int version, Instant exportedAt, UUID sourceSpaceId, String spaceName, List<ArchiveDiary> diaries) {
            this.version=version; this.exportedAt=exportedAt; this.sourceSpaceId=sourceSpaceId;
            this.spaceName=spaceName; this.diaries=diaries;
        }
    }
    public static final class ArchiveDiary {
        public UUID id; public String title; public LocalDate diaryDate; public String contentHtml; public String mood;
        public String visibility; public boolean locked; public List<ArchiveTag> tags;
        public List<ArchiveMedia> media; public List<ArchiveComment> comments;
        public ArchiveDiary() {}
        ArchiveDiary(UUID id,String title,LocalDate diaryDate,String contentHtml,String mood,String visibility,
                     boolean locked,List<ArchiveTag> tags,List<ArchiveMedia> media,List<ArchiveComment> comments) {
            this.id=id; this.title=title; this.diaryDate=diaryDate; this.contentHtml=contentHtml; this.mood=mood;
            this.visibility=visibility; this.locked=locked; this.tags=new ArrayList<>(tags);
            this.media=new ArrayList<>(media); this.comments=new ArrayList<>(comments);
        }
    }
    public static final class ArchiveTag {
        public String name; public String color; public ArchiveTag() {}
        ArchiveTag(String name,String color){this.name=name;this.color=color;}
    }
    public static final class ArchiveMedia {
        public UUID id; public String path; public String originalFilename; public String mediaType;
        public String contentType; public long sizeBytes; public String caption; public LocalDateTime takenAt;
        public int position; public transient String storageKey;
        public ArchiveMedia() {}
        ArchiveMedia(UUID id,String path,String originalFilename,String mediaType,String contentType,long sizeBytes,
                     String caption,LocalDateTime takenAt,int position,String storageKey) {
            this.id=id;this.path=path;this.originalFilename=originalFilename;this.mediaType=mediaType;
            this.contentType=contentType;this.sizeBytes=sizeBytes;this.caption=caption;this.takenAt=takenAt;
            this.position=position;this.storageKey=storageKey;
        }
    }
    public static final class ArchiveComment {
        public String author; public String content; public LocalDateTime createdAt; public ArchiveComment() {}
        ArchiveComment(String author,String content,LocalDateTime createdAt){this.author=author;this.content=content;this.createdAt=createdAt;}
    }
}
