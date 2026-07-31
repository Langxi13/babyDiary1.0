package com.langxi.babydiary.transfer.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class PortableArchiveFormat {
    static final int VERSION = 3;
    static final int MAX_DIARIES = 2_000;
    static final int MAX_ENTRIES = 10_000;
    static final long MAX_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024;
    static final long MAX_ENTRY_BYTES = 256L * 1024 * 1024;
    static final long MAX_MEDIA_BYTES = 100L * 1024 * 1024;
    static final long MAX_MANIFEST_BYTES = 5L * 1024 * 1024;

    private PortableArchiveFormat() {}

    static final class Manifest {
        public int version;
        public Instant exportedAt;
        public UUID sourceSpaceId;
        public String spaceName;
        public List<Diary> diaries;

        public Manifest() {}

        Manifest(
                int version,
                Instant exportedAt,
                UUID sourceSpaceId,
                String spaceName,
                List<Diary> diaries) {
            this.version = version;
            this.exportedAt = exportedAt;
            this.sourceSpaceId = sourceSpaceId;
            this.spaceName = spaceName;
            this.diaries = diaries;
        }
    }

    static final class Diary {
        public UUID id;
        public String title;
        public LocalDate diaryDate;
        public String contentHtml;
        public String mood;
        public String visibility;
        public boolean locked;
        public List<Tag> tags;
        public List<Media> media;
        public List<Comment> comments;

        public Diary() {}

        Diary(
                UUID id,
                String title,
                LocalDate diaryDate,
                String contentHtml,
                String mood,
                String visibility,
                boolean locked,
                List<Tag> tags,
                List<Media> media,
                List<Comment> comments) {
            this.id = id;
            this.title = title;
            this.diaryDate = diaryDate;
            this.contentHtml = contentHtml;
            this.mood = mood;
            this.visibility = visibility;
            this.locked = locked;
            this.tags = new ArrayList<>(tags);
            this.media = new ArrayList<>(media);
            this.comments = new ArrayList<>(comments);
        }
    }

    static final class Tag {
        public String name;
        public String color;

        public Tag() {}

        Tag(String name, String color) {
            this.name = name;
            this.color = color;
        }
    }

    static final class Media {
        public UUID id;
        public String path;
        public String originalFilename;
        public String mediaType;
        public String contentType;
        public long sizeBytes;
        public String caption;
        public LocalDateTime takenAt;
        public int position;
        public transient String storageProvider;
        public transient String storageKey;

        public Media() {}

        Media(
                UUID id,
                String path,
                String originalFilename,
                String mediaType,
                String contentType,
                long sizeBytes,
                String caption,
                LocalDateTime takenAt,
                int position,
                String storageProvider,
                String storageKey) {
            this.id = id;
            this.path = path;
            this.originalFilename = originalFilename;
            this.mediaType = mediaType;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.caption = caption;
            this.takenAt = takenAt;
            this.position = position;
            this.storageProvider = storageProvider;
            this.storageKey = storageKey;
        }
    }

    static final class Comment {
        public String author;
        public String content;
        public LocalDateTime createdAt;

        public Comment() {}

        Comment(String author, String content, LocalDateTime createdAt) {
            this.author = author;
            this.content = content;
            this.createdAt = createdAt;
        }
    }
}
