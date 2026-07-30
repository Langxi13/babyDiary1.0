package com.langxi.babydiary.notification.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper {
    @Select(
            """
            SELECT n.public_id,s.public_id AS space_public_id,n.type,n.title,n.body,n.target_ref,n.read_at,n.created_at
            FROM notification n LEFT JOIN diary_space s ON s.space_id=n.space_id
            WHERE n.account_id=#{accountId}
            ORDER BY n.created_at DESC,n.notification_id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Row> findPage(
            @Param("accountId") long accountId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM notification WHERE account_id=#{accountId}")
    long count(long accountId);

    @Select("SELECT COUNT(*) FROM notification WHERE account_id=#{accountId} AND read_at IS NULL")
    long countUnread(long accountId);

    @Update(
            "UPDATE notification SET read_at=COALESCE(read_at,#{now}) WHERE account_id=#{accountId} AND public_id=#{publicId}")
    void markRead(
            @Param("accountId") long accountId,
            @Param("publicId") byte[] publicId,
            @Param("now") LocalDateTime now);

    @Update(
            "UPDATE notification SET read_at=#{now} WHERE account_id=#{accountId} AND read_at IS NULL")
    void markAllRead(@Param("accountId") long accountId, @Param("now") LocalDateTime now);

    final class Row {
        private byte[] publicId;
        private byte[] spacePublicId;
        private String type;
        private String title;
        private String body;
        private String targetRef;
        private LocalDateTime readAt;
        private LocalDateTime createdAt;

        public Row() {}

        public byte[] publicId() {
            return publicId;
        }

        public byte[] spacePublicId() {
            return spacePublicId;
        }

        public String type() {
            return type;
        }

        public String title() {
            return title;
        }

        public String body() {
            return body;
        }

        public String targetRef() {
            return targetRef;
        }

        public LocalDateTime readAt() {
            return readAt;
        }

        public LocalDateTime createdAt() {
            return createdAt;
        }

        public void setPublicId(byte[] publicId) {
            this.publicId = publicId;
        }

        public void setSpacePublicId(byte[] spacePublicId) {
            this.spacePublicId = spacePublicId;
        }

        public void setType(String type) {
            this.type = type;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public void setTargetRef(String targetRef) {
            this.targetRef = targetRef;
        }

        public void setReadAt(LocalDateTime readAt) {
            this.readAt = readAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
