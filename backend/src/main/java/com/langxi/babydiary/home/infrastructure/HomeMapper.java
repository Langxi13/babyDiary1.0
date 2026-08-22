package com.langxi.babydiary.home.infrastructure;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HomeMapper {
    List<DiaryRow> findRecentDiaries(
            @Param("spaceId") long spaceId, @Param("accountId") long accountId);

    List<DraftRow> findDrafts(@Param("spaceId") long spaceId, @Param("accountId") long accountId);

    List<AnniversaryRow> findAnniversaries(@Param("spaceId") long spaceId);

    List<FavoriteRow> findFavorites(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("elevated") boolean elevated);

    final class DiaryRow {
        private long diaryId;
        private byte[] diaryPublicId;
        private String title;
        private LocalDate diaryDate;
        private String contentSnippet;
        private String mood;
        private String visibility;
        private boolean locked;
        private int version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private long diaryTotal;
        private byte[] tagPublicId;
        private String tagName;
        private String tagColor;
        private byte[] mediaPublicId;
        private String mediaType;
        private Integer mediaPosition;
        private String mediaStatus;
        private boolean protectedContent;
        private String thumbnailProfile;
        private String previewProfile;
        private long mediaCount;

        public long diaryId() {
            return diaryId;
        }

        public byte[] diaryPublicId() {
            return diaryPublicId;
        }

        public String title() {
            return title;
        }

        public LocalDate diaryDate() {
            return diaryDate;
        }

        public String contentSnippet() {
            return contentSnippet;
        }

        public String mood() {
            return mood;
        }

        public String visibility() {
            return visibility;
        }

        public boolean locked() {
            return locked;
        }

        public int version() {
            return version;
        }

        public LocalDateTime createdAt() {
            return createdAt;
        }

        public LocalDateTime updatedAt() {
            return updatedAt;
        }

        public long diaryTotal() {
            return diaryTotal;
        }

        public byte[] tagPublicId() {
            return tagPublicId;
        }

        public String tagName() {
            return tagName;
        }

        public String tagColor() {
            return tagColor;
        }

        public byte[] mediaPublicId() {
            return mediaPublicId;
        }

        public String mediaType() {
            return mediaType;
        }

        public Integer mediaPosition() {
            return mediaPosition;
        }

        public String mediaStatus() {
            return mediaStatus;
        }

        public boolean protectedContent() {
            return protectedContent;
        }

        public String thumbnailProfile() {
            return thumbnailProfile;
        }

        public String previewProfile() {
            return previewProfile;
        }

        public long mediaCount() {
            return mediaCount;
        }

        public void setDiaryId(long value) {
            diaryId = value;
        }

        public void setDiaryPublicId(byte[] value) {
            diaryPublicId = value;
        }

        public void setTitle(String value) {
            title = value;
        }

        public void setDiaryDate(LocalDate value) {
            diaryDate = value;
        }

        public void setContentSnippet(String value) {
            contentSnippet = value;
        }

        public void setMood(String value) {
            mood = value;
        }

        public void setVisibility(String value) {
            visibility = value;
        }

        public void setLocked(boolean value) {
            locked = value;
        }

        public void setVersion(int value) {
            version = value;
        }

        public void setCreatedAt(LocalDateTime value) {
            createdAt = value;
        }

        public void setUpdatedAt(LocalDateTime value) {
            updatedAt = value;
        }

        public void setDiaryTotal(long value) {
            diaryTotal = value;
        }

        public void setTagPublicId(byte[] value) {
            tagPublicId = value;
        }

        public void setTagName(String value) {
            tagName = value;
        }

        public void setTagColor(String value) {
            tagColor = value;
        }

        public void setMediaPublicId(byte[] value) {
            mediaPublicId = value;
        }

        public void setMediaType(String value) {
            mediaType = value;
        }

        public void setMediaPosition(Integer value) {
            mediaPosition = value;
        }

        public void setMediaStatus(String value) {
            mediaStatus = value;
        }

        public void setProtectedContent(boolean value) {
            protectedContent = value;
        }

        public void setThumbnailProfile(String value) {
            thumbnailProfile = value;
        }

        public void setPreviewProfile(String value) {
            previewProfile = value;
        }

        public void setMediaCount(long value) {
            mediaCount = value;
        }
    }

    final class DraftRow {
        private byte[] publicId;
        private String draftKey;
        private byte[] diaryPublicId;
        private String payloadJson;
        private LocalDateTime updatedAt;

        public byte[] publicId() {
            return publicId;
        }

        public String draftKey() {
            return draftKey;
        }

        public byte[] diaryPublicId() {
            return diaryPublicId;
        }

        public String payloadJson() {
            return payloadJson;
        }

        public LocalDateTime updatedAt() {
            return updatedAt;
        }

        public void setPublicId(byte[] value) {
            publicId = value;
        }

        public void setDraftKey(String value) {
            draftKey = value;
        }

        public void setDiaryPublicId(byte[] value) {
            diaryPublicId = value;
        }

        public void setPayloadJson(String value) {
            payloadJson = value;
        }

        public void setUpdatedAt(LocalDateTime value) {
            updatedAt = value;
        }
    }

    final class AnniversaryRow {
        private byte[] publicId;
        private String title;
        private LocalDate anniversaryDate;

        public byte[] publicId() {
            return publicId;
        }

        public String title() {
            return title;
        }

        public LocalDate anniversaryDate() {
            return anniversaryDate;
        }

        public void setPublicId(byte[] value) {
            publicId = value;
        }

        public void setTitle(String value) {
            title = value;
        }

        public void setAnniversaryDate(LocalDate value) {
            anniversaryDate = value;
        }
    }

    final class FavoriteRow {
        private byte[] publicId;
        private String mediaType;
        private String status;
        private boolean protectedContent;
        private String thumbnailProfile;
        private String previewProfile;

        public byte[] publicId() {
            return publicId;
        }

        public String mediaType() {
            return mediaType;
        }

        public String status() {
            return status;
        }

        public boolean protectedContent() {
            return protectedContent;
        }

        public String thumbnailProfile() {
            return thumbnailProfile;
        }

        public String previewProfile() {
            return previewProfile;
        }

        public void setPublicId(byte[] value) {
            publicId = value;
        }

        public void setMediaType(String value) {
            mediaType = value;
        }

        public void setStatus(String value) {
            status = value;
        }

        public void setProtectedContent(boolean value) {
            protectedContent = value;
        }

        public void setThumbnailProfile(String value) {
            thumbnailProfile = value;
        }

        public void setPreviewProfile(String value) {
            previewProfile = value;
        }
    }
}
