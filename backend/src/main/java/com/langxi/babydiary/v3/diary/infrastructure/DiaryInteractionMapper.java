package com.langxi.babydiary.v3.diary.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DiaryInteractionMapper {
    @Select("""
            SELECT d.diary_id FROM diary d JOIN space_member m ON m.space_id=d.space_id
            WHERE d.space_id=#{spaceId} AND d.public_id=#{diaryPublicId} AND d.deleted_at IS NULL
              AND m.account_id=#{accountId} AND m.status='ACTIVE'
              AND (d.visibility='SHARED' OR d.author_id=#{accountId})
            """)
    Long findVisibleDiary(@Param("spaceId") long spaceId, @Param("diaryPublicId") byte[] diaryPublicId,
                          @Param("accountId") long accountId);

    @Select("""
            SELECT c.comment_id,c.public_id,a.public_id AS author_public_id,a.username,c.content,
                   c.created_at,c.updated_at,ma.public_id AS avatar_asset_public_id,
                   avs.public_id AS avatar_space_public_id
            FROM diary_comment c JOIN account a ON a.account_id=c.author_id
            LEFT JOIN user_avatar ua ON ua.account_id=a.account_id
            LEFT JOIN media_asset ma ON ma.asset_id=ua.asset_id AND ma.deleted_at IS NULL AND ma.status='READY'
            LEFT JOIN diary_space avs ON avs.space_id=ma.space_id
            WHERE c.diary_id=#{diaryId} AND c.deleted_at IS NULL
            ORDER BY c.created_at,c.comment_id
            """)
    List<CommentRow> findComments(long diaryId);

    @Insert("INSERT INTO diary_comment(public_id,diary_id,author_id,content) " +
            "VALUES(#{publicId},#{diaryId},#{authorId},#{content})")
    @Options(useGeneratedKeys = true, keyProperty = "commentId")
    void insertComment(CommentInsert row);

    @Update("UPDATE diary_comment SET content=#{content},updated_at=UTC_TIMESTAMP(6) " +
            "WHERE diary_id=#{diaryId} AND public_id=#{publicId} AND author_id=#{authorId} AND deleted_at IS NULL")
    int updateComment(@Param("diaryId") long diaryId, @Param("publicId") byte[] publicId,
                      @Param("authorId") long authorId, @Param("content") String content);

    @Update("UPDATE diary_comment SET deleted_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) " +
            "WHERE diary_id=#{diaryId} AND public_id=#{publicId} AND author_id=#{authorId} AND deleted_at IS NULL")
    int deleteComment(@Param("diaryId") long diaryId, @Param("publicId") byte[] publicId,
                      @Param("authorId") long authorId);

    @Select("""
            SELECT r.emoji,COUNT(*) AS reaction_count,
                   MAX(CASE WHEN r.account_id=#{accountId} THEN 1 ELSE 0 END) AS reacted_by_me
            FROM diary_reaction r WHERE r.diary_id=#{diaryId}
            GROUP BY r.emoji ORDER BY MIN(r.created_at),r.emoji
            """)
    List<ReactionRow> findReactions(@Param("diaryId") long diaryId, @Param("accountId") long accountId);

    @Insert("INSERT IGNORE INTO diary_reaction(diary_id,account_id,emoji) VALUES(#{diaryId},#{accountId},#{emoji})")
    void insertReaction(@Param("diaryId") long diaryId, @Param("accountId") long accountId,
                        @Param("emoji") String emoji);

    @Delete("DELETE FROM diary_reaction WHERE diary_id=#{diaryId} AND account_id=#{accountId} AND emoji=#{emoji}")
    void deleteReaction(@Param("diaryId") long diaryId, @Param("accountId") long accountId,
                        @Param("emoji") String emoji);

    final class CommentInsert {
        private Long commentId;
        private final byte[] publicId;
        private final long diaryId;
        private final long authorId;
        private final String content;
        public CommentInsert(byte[] publicId, long diaryId, long authorId, String content) {
            this.publicId=publicId; this.diaryId=diaryId; this.authorId=authorId; this.content=content;
        }
        public Long getCommentId(){return commentId;} public void setCommentId(Long value){commentId=value;}
        public byte[] getPublicId(){return publicId;} public long getDiaryId(){return diaryId;}
        public long getAuthorId(){return authorId;} public String getContent(){return content;}
    }

    final class CommentRow {
        private long commentId; private byte[] publicId; private byte[] authorPublicId; private String username;
        private String content; private LocalDateTime createdAt; private LocalDateTime updatedAt;
        private byte[] avatarAssetPublicId; private byte[] avatarSpacePublicId;
        public CommentRow() {}
        public long getCommentId(){return commentId;} public byte[] getPublicId(){return publicId;}
        public byte[] getAuthorPublicId(){return authorPublicId;} public String getUsername(){return username;}
        public String getContent(){return content;} public LocalDateTime getCreatedAt(){return createdAt;}
        public LocalDateTime getUpdatedAt(){return updatedAt;} public byte[] getAvatarAssetPublicId(){return avatarAssetPublicId;}
        public byte[] getAvatarSpacePublicId(){return avatarSpacePublicId;}
        public void setCommentId(long v){commentId=v;} public void setPublicId(byte[] v){publicId=v;}
        public void setAuthorPublicId(byte[] v){authorPublicId=v;} public void setUsername(String v){username=v;}
        public void setContent(String v){content=v;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
        public void setUpdatedAt(LocalDateTime v){updatedAt=v;} public void setAvatarAssetPublicId(byte[] v){avatarAssetPublicId=v;}
        public void setAvatarSpacePublicId(byte[] v){avatarSpacePublicId=v;}
    }

    record ReactionRow(String emoji, long reactionCount, boolean reactedByMe) {}
}
