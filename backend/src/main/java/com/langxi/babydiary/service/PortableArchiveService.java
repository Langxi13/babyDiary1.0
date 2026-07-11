package com.langxi.babydiary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.DiaryWriteDTO;
import com.langxi.babydiary.dto.ImportResultVO;
import com.langxi.babydiary.entity.*;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.CollaborationMapper;
import com.langxi.babydiary.mapper.DiaryImageMapper;
import com.langxi.babydiary.storage.StoredObject;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class PortableArchiveService {
    private static final int MAX_ARCHIVE_ENTRIES = 10_000;
    private static final long MAX_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 256L * 1024 * 1024;
    private static final long MAX_LEGACY_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PRIVATE_IMAGE_BYTES = 25L * 1024 * 1024;
    private static final long MAX_MANIFEST_BYTES = 5L * 1024 * 1024;

    private final SpaceService spaceService;
    private final CollaborationMapper collaborationMapper;
    private final DiaryImageMapper imageMapper;
    private final TagService tagService;
    private final CollaborativeDiaryService diaryService;
    private final ImageStorageService imageStorageService;
    private final AccountSecurityService accountSecurityService;
    private final MediaService mediaService;
    private final ObjectMapper objectMapper;

    public PortableArchiveService(SpaceService spaceService,
                                  CollaborationMapper collaborationMapper,
                                  DiaryImageMapper imageMapper,
                                  TagService tagService,
                                  CollaborativeDiaryService diaryService,
                                  ImageStorageService imageStorageService,
                                  AccountSecurityService accountSecurityService,
                                  MediaService mediaService,
                                  ObjectMapper objectMapper) {
        this.spaceService = spaceService;
        this.collaborationMapper = collaborationMapper;
        this.imageMapper = imageMapper;
        this.tagService = tagService;
        this.diaryService = diaryService;
        this.imageStorageService = imageStorageService;
        this.accountSecurityService = accountSecurityService;
        this.mediaService = mediaService;
        this.objectMapper = objectMapper;
    }

    public FileSystemResource exportSpace(String spacePublicId, Integer userId, String stepUpToken) throws IOException {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        List<Diary> diaries = collaborationMapper.findExportDiaries(space.getSpaceId(), userId);
        if (diaries.stream().anyMatch(diary -> Boolean.TRUE.equals(diary.getLocked()))) {
            accountSecurityService.requireStepUp(userId, stepUpToken);
        }

        ArchiveManifest manifest = new ArchiveManifest();
        manifest.version = 2;
        manifest.exportedAt = Timestamp.from(Instant.now()).toString();
        manifest.spaceName = space.getName();
        manifest.diaries = new ArrayList<>();

        Path archive = Files.createTempFile("baby-diary-export-", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Diary diary : diaries) {
                ArchiveDiary record = ArchiveDiary.from(diary);
                record.tags = tagService.findTagsByDiaryId(diary.getDiaryId()).stream()
                        .map(ArchiveTag::from).toList();
                record.comments = collaborationMapper.findComments(diary.getDiaryId()).stream()
                        .map(ArchiveComment::from).toList();
                record.images = new ArrayList<>();
                record.media = new ArrayList<>();
                List<String> imagePaths = imageMapper.findImagePathsByDiaryId(diary.getDiaryId());
                for (int index = 0; index < imagePaths.size(); index++) {
                    String imagePath = imagePaths.get(index);
                    Path source = imageStorageService.resolveImagePath(imagePath);
                    if (!Files.isRegularFile(source)) continue;
                    String extension = extensionOf(imagePath);
                    String archivePath = "media/" + diary.getPublicId() + "/" + (index + 1) + extension;
                    zip.putNextEntry(new ZipEntry(archivePath));
                    Files.copy(source, zip);
                    zip.closeEntry();
                    record.images.add(archivePath);
                }
                for (MediaAsset asset : mediaService.findAssetsByDiary(diary.getDiaryId())) {
                    String extension = extensionOf(asset.getStorageKey());
                    String archivePath = "objects/" + diary.getPublicId() + "/" + asset.getPublicId() + extension;
                    try (StoredObject object = mediaService.openOriginalForExport(asset)) {
                        zip.putNextEntry(new ZipEntry(archivePath));
                        try {
                            object.stream().transferTo(zip);
                            record.media.add(ArchiveMedia.from(asset, archivePath));
                        } finally {
                            zip.closeEntry();
                        }
                    } catch (IOException missingObject) {
                        // Keep the rest of the export usable when one stored object is missing.
                    }
                }
                manifest.diaries.add(record);
            }
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
            zip.closeEntry();
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(archive);
            if (exception instanceof IOException ioException) throw ioException;
            throw exception;
        }
        return new TemporaryFileResource(archive);
    }

    @Transactional
    public ImportResultVO importSpace(String spacePublicId, Integer userId, MultipartFile archiveFile,
                                      String stepUpToken) throws IOException {
        if (archiveFile == null || archiveFile.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择导入文件");
        }
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        try (ArchiveContents entries = readArchive(archiveFile)) {
            Path manifestPath = entries.get("manifest.json");
            if (manifestPath == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "导入包缺少manifest.json");
            if (Files.size(manifestPath) > MAX_MANIFEST_BYTES) {
                throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED, "导入包清单过大");
            }
            ArchiveManifest manifest = objectMapper.readValue(manifestPath.toFile(), ArchiveManifest.class);
            if ((manifest.version != 1 && manifest.version != 2) || manifest.diaries == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持该导入包版本");
            }

            int importedDiaries = 0;
            int importedImages = 0;
            int importedMedia = 0;
            int skippedDiaries = 0;
            for (ArchiveDiary record : manifest.diaries) {
                String importedPublicId = resolveImportPublicId(space, record.publicId);
                if (importedPublicId == null) {
                    skippedDiaries++;
                    continue;
                }
                DiaryWriteDTO dto = new DiaryWriteDTO();
                dto.setClientId(importedPublicId);
                dto.setTitle(record.title);
                dto.setDate(record.date);
                dto.setContent(record.content);
                dto.setContentFormat(record.contentFormat == null ? "html" : record.contentFormat);
                dto.setMoodKey(record.moodKey);
                dto.setVisibility(record.visibility == null ? "PRIVATE" : record.visibility);
                dto.setLocked(Boolean.TRUE.equals(record.locked));
                List<Integer> tagIds = new ArrayList<>();
                if (record.tags != null) {
                    for (ArchiveTag tag : record.tags) {
                        tagIds.add(tagService.createTag(userId, space.getSpaceId(), tag.name, tag.color).getTagId());
                    }
                }
                dto.setTagIds(tagIds);
                diaryService.create(spacePublicId, userId, dto, stepUpToken);
                Diary imported = collaborationMapper.findDiary(space.getSpaceId(), importedPublicId);

                List<DiaryImage> images = new ArrayList<>();
                if (record.images != null) {
                    int sort = 1;
                    for (String archivePath : record.images) {
                        Path imagePath = entries.get(archivePath);
                        if (imagePath == null) continue;
                        long imageSize = Files.size(imagePath);
                        long imageLimit = Boolean.TRUE.equals(record.locked)
                                ? MAX_PRIVATE_IMAGE_BYTES : MAX_LEGACY_IMAGE_BYTES;
                        if (imageSize > imageLimit) continue;
                        if (Boolean.TRUE.equals(record.locked)) {
                            mediaService.upload(spacePublicId, userId,
                                    new PathMultipartFile("file", imagePath.getFileName().toString(),
                                            contentType(archivePath), imagePath),
                                    importedPublicId, null, null, null, null, null, stepUpToken);
                            importedMedia++;
                            continue;
                        }
                        byte[] bytes = Files.readAllBytes(imagePath);
                        String stored = imageStorageService.storeImageBytes(
                                bytes, contentType(archivePath), "diary_" + userId + "_", true);
                        DiaryImage image = new DiaryImage();
                        image.setDiaryId(imported.getDiaryId());
                        image.setImagePath(stored);
                        image.setSort(sort++);
                        images.add(image);
                        importedImages++;
                    }
                }
                if (!images.isEmpty()) imageMapper.insertDiaryImages(images.toArray(new DiaryImage[0]));
                if (record.media != null) {
                    for (ArchiveMedia archivedMedia : record.media) {
                        Path mediaPath = entries.get(archivedMedia.path);
                        if (mediaPath == null) continue;
                        mediaService.upload(spacePublicId, userId,
                                new PathMultipartFile("file", archivedMedia.originalFilename,
                                        archivedMedia.contentType, mediaPath),
                                importedPublicId, archivedMedia.caption, archivedMedia.takenAt,
                                archivedMedia.locationName, archivedMedia.latitude, archivedMedia.longitude,
                                stepUpToken);
                        importedMedia++;
                    }
                }
                if (record.comments != null) {
                    for (ArchiveComment archivedComment : record.comments) {
                        DiaryComment comment = new DiaryComment();
                        comment.setPublicId(UUID.randomUUID().toString());
                        comment.setDiaryId(imported.getDiaryId());
                        comment.setUserId(userId);
                        String author = archivedComment.username == null ? "原成员" : archivedComment.username;
                        String content = "[" + author + "] "
                                + (archivedComment.content == null ? "" : archivedComment.content);
                        comment.setContent(content.substring(0, Math.min(2000, content.length())));
                        collaborationMapper.insertComment(comment);
                    }
                }
                importedDiaries++;
            }
            return new ImportResultVO(importedDiaries, importedImages, importedMedia, skippedDiaries);
        }
    }

    private String resolveImportPublicId(DiarySpace targetSpace, String archivedPublicId) {
        if (archivedPublicId == null || archivedPublicId.isBlank()) return null;
        if (collaborationMapper.findDiary(targetSpace.getSpaceId(), archivedPublicId) != null) return null;
        if (collaborationMapper.findDiaryByPublicId(archivedPublicId) == null) return archivedPublicId;

        String scopedSource = targetSpace.getPublicId() + ":" + archivedPublicId;
        String remapped = UUID.nameUUIDFromBytes(scopedSource.getBytes(StandardCharsets.UTF_8)).toString();
        return collaborationMapper.findDiary(targetSpace.getSpaceId(), remapped) == null ? remapped : null;
    }

    private ArchiveContents readArchive(MultipartFile archive) throws IOException {
        Path root = Files.createTempDirectory("baby-diary-import-");
        Map<String, Path> entries = new LinkedHashMap<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(archive.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (++count > MAX_ARCHIVE_ENTRIES) throw new BusinessException(ErrorCode.BAD_REQUEST, "导入包文件数量过多");
                String name = safeEntryName(entry.getName());
                if (entries.containsKey(name)) throw new BusinessException(ErrorCode.BAD_REQUEST, "导入包包含重复路径");
                Path output = root.resolve(name).normalize();
                if (!output.startsWith(root)) throw new BusinessException(ErrorCode.BAD_REQUEST, "导入包包含非法路径");
                Files.createDirectories(output.getParent());
                byte[] buffer = new byte[8192];
                long entryBytes = 0;
                int read;
                try (OutputStream stream = Files.newOutputStream(output)) {
                    while ((read = zip.read(buffer)) >= 0) {
                        entryBytes += read;
                        total += read;
                        if (entryBytes > MAX_ENTRY_BYTES || total > MAX_UNCOMPRESSED_BYTES) {
                            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED, "导入包解压后过大");
                        }
                        stream.write(buffer, 0, read);
                    }
                }
                entries.put(name, output);
                zip.closeEntry();
            }
        } catch (IOException | RuntimeException exception) {
            deleteDirectory(root);
            throw exception;
        }
        return new ArchiveContents(root, entries);
    }

    private String safeEntryName(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入包包含空路径");
        }
        Path path = Path.of(value).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入包包含非法路径");
        }
        return path.toString().replace('\\', '/');
    }

    private String extensionOf(String path) {
        int index = path.lastIndexOf('.');
        return index < 0 ? ".jpg" : path.substring(index).toLowerCase(Locale.ROOT);
    }

    private String contentType(String path) {
        String extension = extensionOf(path);
        return switch (extension) {
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    public static class ArchiveManifest {
        public int version;
        public String exportedAt;
        public String spaceName;
        public List<ArchiveDiary> diaries;
    }

    public static class ArchiveDiary {
        public String publicId;
        public String title;
        public String date;
        public String content;
        public String contentFormat;
        public String moodKey;
        public String visibility;
        public Boolean locked;
        public List<ArchiveTag> tags;
        public List<String> images;
        public List<ArchiveMedia> media;
        public List<ArchiveComment> comments;

        static ArchiveDiary from(Diary diary) {
            ArchiveDiary value = new ArchiveDiary();
            value.publicId = diary.getPublicId();
            value.title = diary.getTitle();
            value.date = diary.getDate().toString();
            value.content = diary.getContent();
            value.contentFormat = diary.getContentFormat();
            value.moodKey = diary.getMoodKey();
            value.visibility = diary.getVisibility();
            value.locked = diary.getLocked();
            return value;
        }
    }

    public static class ArchiveMedia {
        public String path;
        public String mediaType;
        public String originalFilename;
        public String contentType;
        public String caption;
        public String takenAt;
        public String locationName;
        public java.math.BigDecimal latitude;
        public java.math.BigDecimal longitude;

        static ArchiveMedia from(MediaAsset asset, String path) {
            ArchiveMedia value = new ArchiveMedia();
            value.path = path;
            value.mediaType = asset.getMediaType();
            value.originalFilename = asset.getOriginalFilename();
            value.contentType = asset.getContentType();
            value.caption = asset.getCaption();
            value.takenAt = asset.getTakenAt() == null ? null : asset.getTakenAt().toInstant().toString();
            value.locationName = asset.getLocationName();
            value.latitude = asset.getLatitude();
            value.longitude = asset.getLongitude();
            return value;
        }
    }

    public static class ArchiveTag {
        public String name;
        public String color;

        static ArchiveTag from(Tag tag) {
            ArchiveTag value = new ArchiveTag();
            value.name = tag.getName();
            value.color = tag.getColor();
            return value;
        }
    }

    public static class ArchiveComment {
        public String username;
        public String content;

        static ArchiveComment from(DiaryComment comment) {
            ArchiveComment value = new ArchiveComment();
            value.username = comment.getUsername();
            value.content = comment.getContent();
            return value;
        }
    }

    private void deleteDirectory(Path root) {
        if (root == null) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private final class ArchiveContents implements AutoCloseable {
        private final Path root;
        private final Map<String, Path> entries;

        private ArchiveContents(Path root, Map<String, Path> entries) {
            this.root = root;
            this.entries = entries;
        }

        private Path get(String name) {
            return entries.get(safeEntryName(name));
        }

        @Override
        public void close() {
            deleteDirectory(root);
        }
    }
}
