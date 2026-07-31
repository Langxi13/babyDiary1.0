package com.langxi.babydiary.sync.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SyncMapper {
    @Select(
            """
            SELECT c.change_seq,c.entity_type,c.entity_public_id,c.operation,c.revision,
                   a.public_id AS actor_public_id,c.created_at
            FROM sync_change c JOIN account a ON a.account_id=c.actor_id
            WHERE c.space_id=#{spaceId} AND c.change_seq>#{cursor}
              AND (c.entity_type<>'DIARY' OR c.visibility='SHARED' OR c.owner_id=#{accountId})
            ORDER BY c.change_seq LIMIT #{limit}
            """)
    List<Row> findChanges(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("cursor") long cursor,
            @Param("limit") int limit);

    @Select("SELECT COALESCE(baseline_cursor,0) FROM sync_retention WHERE space_id=#{spaceId}")
    Long baselineCursor(long spaceId);

    @Select(
            "SELECT result_code,entity_type,entity_public_id FROM sync_operation "
                    + "WHERE operation_id=#{operationId} AND account_id=#{accountId} AND space_id=#{spaceId} AND expires_at>UTC_TIMESTAMP(6)")
    OperationRow findOperation(
            @Param("operationId") byte[] operationId,
            @Param("accountId") long accountId,
            @Param("spaceId") long spaceId);

    @Insert(
            "INSERT IGNORE INTO sync_operation(operation_id,account_id,space_id,result_code,entity_type,entity_public_id,expires_at) "
                    + "VALUES(#{operationId},#{accountId},#{spaceId},#{resultCode},#{entityType},#{entityPublicId},#{expiresAt})")
    int insertOperation(
            @Param("operationId") byte[] operationId,
            @Param("accountId") long accountId,
            @Param("spaceId") long spaceId,
            @Param("resultCode") String resultCode,
            @Param("entityType") String entityType,
            @Param("entityPublicId") byte[] entityPublicId,
            @Param("expiresAt") LocalDateTime expiresAt);

    final class Row {
        private long changeSeq;
        private String entityType;
        private byte[] entityPublicId;
        private String operation;
        private int revision;
        private byte[] actorPublicId;
        private LocalDateTime createdAt;

        public Row() {}

        public long changeSeq() {
            return changeSeq;
        }

        public String entityType() {
            return entityType;
        }

        public byte[] entityPublicId() {
            return entityPublicId;
        }

        public String operation() {
            return operation;
        }

        public int revision() {
            return revision;
        }

        public byte[] actorPublicId() {
            return actorPublicId;
        }

        public LocalDateTime createdAt() {
            return createdAt;
        }

        public void setChangeSeq(long changeSeq) {
            this.changeSeq = changeSeq;
        }

        public void setEntityType(String entityType) {
            this.entityType = entityType;
        }

        public void setEntityPublicId(byte[] entityPublicId) {
            this.entityPublicId = entityPublicId;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public void setRevision(int revision) {
            this.revision = revision;
        }

        public void setActorPublicId(byte[] actorPublicId) {
            this.actorPublicId = actorPublicId;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    final class OperationRow {
        private String resultCode;
        private String entityType;
        private byte[] entityPublicId;

        public OperationRow() {}

        public String getResultCode() {
            return resultCode;
        }

        public String getEntityType() {
            return entityType;
        }

        public byte[] getEntityPublicId() {
            return entityPublicId;
        }

        public void setResultCode(String v) {
            resultCode = v;
        }

        public void setEntityType(String v) {
            entityType = v;
        }

        public void setEntityPublicId(byte[] v) {
            entityPublicId = v;
        }
    }
}
