package com.langxi.babydiary.identity.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProfileMapper {
    @Select(
            """
            SELECT a.account_id,a.public_id,a.username,a.email,a.email_verified,a.system_role,
                   a.timezone,a.created_at,ma.public_id AS avatar_public_id,s.public_id AS avatar_space_public_id,
                   av.variant_type AS avatar_variant_type,av.profile AS avatar_variant_profile
            FROM account a LEFT JOIN user_avatar ua ON ua.account_id=a.account_id
            LEFT JOIN media_asset ma ON ma.space_id=ua.space_id AND ma.asset_id=ua.asset_id AND ma.deleted_at IS NULL
              AND NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=ma.space_id AND lock_dm.asset_id=ma.asset_id AND lock_d.locked=1
              )
            LEFT JOIN diary_space s ON s.space_id=ua.space_id
            LEFT JOIN media_variant av ON av.variant_id=(
                SELECT candidate.variant_id FROM media_variant candidate
                WHERE candidate.asset_id=ma.asset_id AND candidate.status='READY' AND candidate.deleted_at IS NULL
                  AND candidate.variant_type IN ('THUMBNAIL','ORIGINAL')
                ORDER BY CASE candidate.variant_type WHEN 'THUMBNAIL' THEN 0 ELSE 1 END,
                         CASE candidate.profile WHEN 'compact' THEN 0 WHEN 'source' THEN 1 ELSE 2 END,
                         candidate.variant_id LIMIT 1
            )
            WHERE a.account_id=#{accountId} AND a.deleted_at IS NULL
            """)
    ProfileRow find(long accountId);

    @Update(
            """
            UPDATE account SET username=#{username},email=#{email},email_verified=IF(email<=>#{email},email_verified,false),
              timezone=#{timezone},updated_at=UTC_TIMESTAMP(6)
            WHERE account_id=#{accountId} AND deleted_at IS NULL
            """)
    void update(
            @Param("accountId") long accountId,
            @Param("username") String username,
            @Param("email") String email,
            @Param("timezone") String timezone);

    @Insert(
            """
            INSERT INTO user_avatar(account_id,space_id,asset_id,updated_at)
            VALUES(#{accountId},#{spaceId},#{assetId},UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE space_id=VALUES(space_id),asset_id=VALUES(asset_id),updated_at=UTC_TIMESTAMP(6)
            """)
    void setAvatar(
            @Param("accountId") long accountId,
            @Param("spaceId") long spaceId,
            @Param("assetId") long assetId);

    @Delete("DELETE FROM user_avatar WHERE account_id=#{accountId}")
    void clearAvatar(long accountId);

    final class ProfileRow {
        private long accountId;
        private byte[] publicId;
        private String username;
        private String email;
        private boolean emailVerified;
        private String systemRole;
        private String timezone;
        private java.time.LocalDateTime createdAt;
        private byte[] avatarPublicId;
        private byte[] avatarSpacePublicId;
        private String avatarVariantType;
        private String avatarVariantProfile;

        public ProfileRow() {}

        public long accountId() {
            return accountId;
        }

        public byte[] publicId() {
            return publicId;
        }

        public String username() {
            return username;
        }

        public String email() {
            return email;
        }

        public boolean emailVerified() {
            return emailVerified;
        }

        public String systemRole() {
            return systemRole;
        }

        public String timezone() {
            return timezone;
        }

        public java.time.LocalDateTime createdAt() {
            return createdAt;
        }

        public byte[] avatarPublicId() {
            return avatarPublicId;
        }

        public byte[] avatarSpacePublicId() {
            return avatarSpacePublicId;
        }

        public String avatarVariantType() {
            return avatarVariantType;
        }

        public String avatarVariantProfile() {
            return avatarVariantProfile;
        }

        public void setAccountId(long accountId) {
            this.accountId = accountId;
        }

        public void setPublicId(byte[] publicId) {
            this.publicId = publicId;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public void setEmailVerified(boolean emailVerified) {
            this.emailVerified = emailVerified;
        }

        public void setSystemRole(String systemRole) {
            this.systemRole = systemRole;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }

        public void setCreatedAt(java.time.LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public void setAvatarPublicId(byte[] avatarPublicId) {
            this.avatarPublicId = avatarPublicId;
        }

        public void setAvatarSpacePublicId(byte[] avatarSpacePublicId) {
            this.avatarSpacePublicId = avatarSpacePublicId;
        }

        public void setAvatarVariantType(String avatarVariantType) {
            this.avatarVariantType = avatarVariantType;
        }

        public void setAvatarVariantProfile(String avatarVariantProfile) {
            this.avatarVariantProfile = avatarVariantProfile;
        }
    }
}
