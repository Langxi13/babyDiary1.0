package com.langxi.babydiary.v3.diary.infrastructure;

import com.langxi.babydiary.v3.diary.application.DiaryRepository;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DiaryMapper {
    List<DiaryRow> findPage(@Param("query") DiaryRepository.Query query,
                            @Param("tagPublicId") byte[] tagPublicId);

    long count(@Param("query") DiaryRepository.Query query, @Param("tagPublicId") byte[] tagPublicId);

    DiaryRow findByPublicId(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId,
                            @Param("accountId") long accountId, @Param("includeDeleted") boolean includeDeleted);

    List<TagRow> findTags(@Param("diaryIds") List<Long> diaryIds);

    List<MediaRow> findMedia(@Param("diaryIds") List<Long> diaryIds);

    List<IdRow> resolveTagIds(@Param("spaceId") long spaceId, @Param("publicIds") List<byte[]> publicIds);

    List<IdRow> resolveMediaIds(@Param("spaceId") long spaceId, @Param("publicIds") List<byte[]> publicIds);

    @Insert("""
            INSERT INTO diary(public_id,space_id,author_id,title,diary_date,content_html,content_text,
              mood_key,visibility,locked,version,created_at,updated_at)
            VALUES(#{publicId},#{spaceId},#{authorId},#{title},#{diaryDate},#{contentHtml},#{contentText},
              #{mood},#{visibility},#{locked},1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "diaryId")
    void insert(DiaryInsert row);

    @Update("""
            UPDATE diary SET title=#{diary.title},diary_date=#{diary.diaryDate},
              content_html=#{diary.contentHtml},content_text=#{diary.contentText},mood_key=#{diary.mood},
              visibility=#{diary.visibility},locked=#{diary.locked},version=version+1,updated_at=UTC_TIMESTAMP(6)
            WHERE diary_id=#{diaryId} AND version=#{expectedVersion} AND deleted_at IS NULL
            """)
    int update(@Param("diaryId") long diaryId, @Param("expectedVersion") int expectedVersion,
               @Param("diary") DiaryRepository.UpdatedDiary diary);

    @Update("""
            <script>
            UPDATE diary SET deleted_at=#{deletedAt},version=version+1,updated_at=UTC_TIMESTAMP(6)
            WHERE diary_id=#{diaryId} AND version=#{expectedVersion}
            <if test="deletedAt != null">AND deleted_at IS NULL</if>
            <if test="deletedAt == null">AND deleted_at IS NOT NULL</if>
            </script>
            """)
    int setDeleted(@Param("diaryId") long diaryId, @Param("expectedVersion") int expectedVersion,
                   @Param("deletedAt") LocalDateTime deletedAt);

    @Delete("DELETE FROM diary_tag WHERE diary_id=#{diaryId}")
    void deleteTags(long diaryId);

    @Insert("INSERT INTO diary_tag(space_id,diary_id,tag_id) VALUES(#{spaceId},#{diaryId},#{tagId})")
    void insertTag(@Param("spaceId") long spaceId, @Param("diaryId") long diaryId, @Param("tagId") long tagId);

    @Delete("DELETE FROM diary_media WHERE diary_id=#{diaryId}")
    void deleteMedia(long diaryId);

    @Insert("INSERT INTO diary_media(space_id,diary_id,asset_id,position) VALUES(#{spaceId},#{diaryId},#{assetId},#{position})")
    void insertMedia(@Param("spaceId") long spaceId, @Param("diaryId") long diaryId,
                     @Param("assetId") long assetId, @Param("position") int position);

    @Insert("""
            INSERT INTO diary_revision(diary_id,version,editor_id,snapshot,created_at)
            VALUES(#{diaryId},#{version},#{editorId},#{snapshotJson},#{createdAt})
            """)
    void insertRevision(@Param("diaryId") long diaryId, @Param("version") int version,
                        @Param("editorId") long editorId, @Param("snapshotJson") String snapshotJson,
                        @Param("createdAt") LocalDateTime createdAt);

    @Select("""
            SELECT revision_id AS id,version,editor_id,snapshot AS snapshot_json,created_at
            FROM diary_revision WHERE diary_id=#{diaryId} AND revision_id=#{revisionId}
            """)
    DiaryRepository.Revision findRevision(@Param("diaryId") long diaryId, @Param("revisionId") long revisionId);

    @Select("""
            SELECT revision_id AS id,version,editor_id,created_at
            FROM diary_revision WHERE diary_id=#{diaryId} ORDER BY version DESC,revision_id DESC
            """)
    List<DiaryRepository.RevisionSummary> findRevisions(long diaryId);

    final class DiaryRow {
        private long diaryId;
        private byte[] publicId;
        private byte[] spacePublicId;
        private long authorId;
        private String title;
        private LocalDate diaryDate;
        private String contentHtml;
        private String contentText;
        private String moodKey;
        private String visibility;
        private boolean locked;
        private int version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime deletedAt;

        public DiaryRow() {
        }

        public long diaryId() { return diaryId; }
        public byte[] publicId() { return publicId; }
        public byte[] spacePublicId() { return spacePublicId; }
        public long authorId() { return authorId; }
        public String title() { return title; }
        public LocalDate diaryDate() { return diaryDate; }
        public String contentHtml() { return contentHtml; }
        public String contentText() { return contentText; }
        public String moodKey() { return moodKey; }
        public String visibility() { return visibility; }
        public boolean locked() { return locked; }
        public int version() { return version; }
        public LocalDateTime createdAt() { return createdAt; }
        public LocalDateTime updatedAt() { return updatedAt; }
        public LocalDateTime deletedAt() { return deletedAt; }

        public void setDiaryId(long diaryId) { this.diaryId = diaryId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setSpacePublicId(byte[] spacePublicId) { this.spacePublicId = spacePublicId; }
        public void setAuthorId(long authorId) { this.authorId = authorId; }
        public void setTitle(String title) { this.title = title; }
        public void setDiaryDate(LocalDate diaryDate) { this.diaryDate = diaryDate; }
        public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }
        public void setContentText(String contentText) { this.contentText = contentText; }
        public void setMoodKey(String moodKey) { this.moodKey = moodKey; }
        public void setVisibility(String visibility) { this.visibility = visibility; }
        public void setLocked(boolean locked) { this.locked = locked; }
        public void setVersion(int version) { this.version = version; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    }

    final class TagRow {
        private long diaryId;
        private long tagId;
        private byte[] publicId;
        private String name;
        private String color;

        public TagRow() {
        }

        public long diaryId() { return diaryId; }
        public long tagId() { return tagId; }
        public byte[] publicId() { return publicId; }
        public String name() { return name; }
        public String color() { return color; }

        public void setDiaryId(long diaryId) { this.diaryId = diaryId; }
        public void setTagId(long tagId) { this.tagId = tagId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setName(String name) { this.name = name; }
        public void setColor(String color) { this.color = color; }
    }

    final class MediaRow {
        private long diaryId;
        private long assetId;
        private byte[] publicId;
        private String mediaType;
        private String caption;
        private LocalDateTime takenAt;
        private int position;
        private String status;
        private String originalProfile;
        private String thumbnailProfile;

        public MediaRow() {
        }

        public long diaryId() { return diaryId; }
        public long assetId() { return assetId; }
        public byte[] publicId() { return publicId; }
        public String mediaType() { return mediaType; }
        public String caption() { return caption; }
        public LocalDateTime takenAt() { return takenAt; }
        public int position() { return position; }
        public String status() { return status; }
        public String originalProfile() { return originalProfile; }
        public String thumbnailProfile() { return thumbnailProfile; }

        public void setDiaryId(long diaryId) { this.diaryId = diaryId; }
        public void setAssetId(long assetId) { this.assetId = assetId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setMediaType(String mediaType) { this.mediaType = mediaType; }
        public void setCaption(String caption) { this.caption = caption; }
        public void setTakenAt(LocalDateTime takenAt) { this.takenAt = takenAt; }
        public void setPosition(int position) { this.position = position; }
        public void setStatus(String status) { this.status = status; }
        public void setOriginalProfile(String originalProfile) { this.originalProfile = originalProfile; }
        public void setThumbnailProfile(String thumbnailProfile) { this.thumbnailProfile = thumbnailProfile; }
    }

    final class IdRow {
        private long internalId;
        private byte[] publicId;

        public IdRow() {
        }

        public long internalId() { return internalId; }
        public byte[] publicId() { return publicId; }

        public void setInternalId(long internalId) { this.internalId = internalId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
    }

    final class DiaryInsert {
        private Long diaryId;
        private final byte[] publicId;
        private final long spaceId;
        private final long authorId;
        private final String title;
        private final LocalDate diaryDate;
        private final String contentHtml;
        private final String contentText;
        private final String mood;
        private final String visibility;
        private final boolean locked;

        DiaryInsert(byte[] publicId, long spaceId, long authorId, String title, LocalDate diaryDate,
                    String contentHtml, String contentText, String mood, String visibility, boolean locked) {
            this.publicId = publicId;
            this.spaceId = spaceId;
            this.authorId = authorId;
            this.title = title;
            this.diaryDate = diaryDate;
            this.contentHtml = contentHtml;
            this.contentText = contentText;
            this.mood = mood;
            this.visibility = visibility;
            this.locked = locked;
        }

        public Long getDiaryId() { return diaryId; }
        public void setDiaryId(Long diaryId) { this.diaryId = diaryId; }
        public byte[] getPublicId() { return publicId; }
        public long getSpaceId() { return spaceId; }
        public long getAuthorId() { return authorId; }
        public String getTitle() { return title; }
        public LocalDate getDiaryDate() { return diaryDate; }
        public String getContentHtml() { return contentHtml; }
        public String getContentText() { return contentText; }
        public String getMood() { return mood; }
        public String getVisibility() { return visibility; }
        public boolean isLocked() { return locked; }
    }
}
