package com.langxi.babydiary.v3.space.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SpaceMapper {
    @Select("""
            SELECT s.public_id,s.name,s.type,m.role,s.default_visibility,s.storage_quota_bytes,
                   COALESCE(u.used_bytes,0) AS storage_used_bytes
            FROM space_member m JOIN diary_space s ON s.space_id=m.space_id
            LEFT JOIN space_storage_usage u ON u.space_id=s.space_id
            WHERE m.account_id=#{accountId} AND m.status='ACTIVE' AND s.deleted_at IS NULL
            ORDER BY CASE s.type WHEN 'PERSONAL' THEN 0 ELSE 1 END,s.created_at,s.space_id
            """)
    List<SpaceRow> findForAccount(long accountId);

    @Select("""
            SELECT s.space_id,s.public_id,m.role,s.type,s.default_visibility,s.storage_quota_bytes,
                   COALESCE(u.used_bytes,0) AS storage_used_bytes
            FROM diary_space s JOIN space_member m ON m.space_id=s.space_id
            LEFT JOIN space_storage_usage u ON u.space_id=s.space_id
            WHERE s.public_id=#{publicId} AND m.account_id=#{accountId}
              AND m.status='ACTIVE' AND s.deleted_at IS NULL
            """)
    ContextRow findContext(@Param("publicId") byte[] publicId, @Param("accountId") long accountId);

    @Insert("""
            INSERT INTO diary_space(public_id,name,type,created_by,default_visibility,storage_quota_bytes)
            VALUES(#{publicId},#{name},'SHARED',#{createdBy},#{defaultVisibility},#{quotaBytes})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "spaceId")
    void insert(SpaceInsert row);

    @Insert("INSERT INTO diary_space(public_id,name,type,created_by,personal_owner_id,default_visibility,storage_quota_bytes) " +
            "VALUES(#{publicId},#{name},'PERSONAL',#{createdBy},#{createdBy},'PRIVATE',#{quotaBytes})")
    @Options(useGeneratedKeys = true, keyProperty = "spaceId")
    void insertPersonal(SpaceInsert row);

    @Insert("INSERT INTO space_member(space_id,account_id,role,status) VALUES(#{spaceId},#{accountId},'OWNER','ACTIVE')")
    void insertOwner(@Param("spaceId") long spaceId, @Param("accountId") long accountId);

    @Insert("INSERT INTO space_storage_usage(space_id,used_bytes) VALUES(#{spaceId},0)")
    void insertStorageUsage(long spaceId);

    @Update("UPDATE diary_space SET name=#{name},default_visibility=#{defaultVisibility} " +
            "WHERE space_id=#{spaceId} AND deleted_at IS NULL")
    int update(@Param("spaceId") long spaceId, @Param("name") String name,
               @Param("defaultVisibility") String defaultVisibility);

    record SpaceRow(byte[] publicId, String name, String type, String role,
                    String defaultVisibility, long storageQuotaBytes, long storageUsedBytes) {
    }

    record ContextRow(long spaceId, byte[] publicId, String role, String type,
                      String defaultVisibility, long storageQuotaBytes, long storageUsedBytes) {
    }

    final class SpaceInsert {
        private Long spaceId;
        private final byte[] publicId;
        private final String name;
        private final long createdBy;
        private final String defaultVisibility;
        private final long quotaBytes;

        SpaceInsert(byte[] publicId, String name, long createdBy, String defaultVisibility, long quotaBytes) {
            this.publicId = publicId;
            this.name = name;
            this.createdBy = createdBy;
            this.defaultVisibility = defaultVisibility;
            this.quotaBytes = quotaBytes;
        }

        public Long getSpaceId() { return spaceId; }
        public void setSpaceId(Long spaceId) { this.spaceId = spaceId; }
        public byte[] getPublicId() { return publicId; }
        public String getName() { return name; }
        public long getCreatedBy() { return createdBy; }
        public String getDefaultVisibility() { return defaultVisibility; }
        public long getQuotaBytes() { return quotaBytes; }
    }
}
