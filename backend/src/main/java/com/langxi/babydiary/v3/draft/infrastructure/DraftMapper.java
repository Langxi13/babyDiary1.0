package com.langxi.babydiary.v3.draft.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DraftMapper {
    @Select("""
            SELECT d.public_id,s.public_id AS space_public_id,d.draft_key,di.public_id AS diary_public_id,
                   d.payload,d.created_at,d.updated_at
            FROM diary_draft d JOIN diary_space s ON s.space_id=d.space_id
            LEFT JOIN diary di ON di.diary_id=d.diary_id
            WHERE d.space_id=#{spaceId} AND d.owner_id=#{ownerId}
            ORDER BY d.updated_at DESC,d.draft_id DESC
            """)
    List<DraftRow> findForOwner(@Param("spaceId") long spaceId, @Param("ownerId") long ownerId);

    @Select("""
            SELECT d.public_id,s.public_id AS space_public_id,d.draft_key,di.public_id AS diary_public_id,
                   d.payload,d.created_at,d.updated_at
            FROM diary_draft d JOIN diary_space s ON s.space_id=d.space_id
            LEFT JOIN diary di ON di.diary_id=d.diary_id
            WHERE d.space_id=#{spaceId} AND d.owner_id=#{ownerId} AND d.draft_key=#{draftKey}
            """)
    DraftRow findByKey(@Param("spaceId") long spaceId, @Param("ownerId") long ownerId,
                       @Param("draftKey") String draftKey);

    @Insert("""
            INSERT INTO diary_draft(public_id,space_id,owner_id,diary_id,draft_key,payload,created_at,updated_at)
            VALUES(#{publicId},#{spaceId},#{ownerId},#{diaryId},#{draftKey},#{payloadJson},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE space_id=VALUES(space_id),diary_id=VALUES(diary_id),payload=VALUES(payload),
              updated_at=UTC_TIMESTAMP(6)
            """)
    void upsert(DraftInsert row);

    @Delete("DELETE FROM diary_draft WHERE space_id=#{spaceId} AND owner_id=#{ownerId} AND draft_key=#{draftKey}")
    void delete(@Param("spaceId") long spaceId, @Param("ownerId") long ownerId, @Param("draftKey") String draftKey);

    record DraftInsert(byte[] publicId, long spaceId, long ownerId, Long diaryId, String draftKey,
                       String payloadJson) {
    }

    final class DraftRow {
        private byte[] publicId;
        private byte[] spacePublicId;
        private String draftKey;
        private byte[] diaryPublicId;
        private String payload;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public DraftRow() {
        }

        public byte[] publicId() { return publicId; }
        public byte[] spacePublicId() { return spacePublicId; }
        public String draftKey() { return draftKey; }
        public byte[] diaryPublicId() { return diaryPublicId; }
        public String payload() { return payload; }
        public LocalDateTime createdAt() { return createdAt; }
        public LocalDateTime updatedAt() { return updatedAt; }

        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setSpacePublicId(byte[] spacePublicId) { this.spacePublicId = spacePublicId; }
        public void setDraftKey(String draftKey) { this.draftKey = draftKey; }
        public void setDiaryPublicId(byte[] diaryPublicId) { this.diaryPublicId = diaryPublicId; }
        public void setPayload(String payload) { this.payload = payload; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
