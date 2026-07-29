package com.langxi.babydiary.v3.identity.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ProfileMapper {
    @Select("""
            SELECT a.account_id,a.public_id,a.username,a.password_hash,a.email,a.email_verified,a.system_role,
                   a.timezone,ma.public_id AS avatar_public_id,s.public_id AS avatar_space_public_id
            FROM account a LEFT JOIN user_avatar ua ON ua.account_id=a.account_id
            LEFT JOIN media_asset ma ON ma.space_id=ua.space_id AND ma.asset_id=ua.asset_id AND ma.deleted_at IS NULL
            LEFT JOIN diary_space s ON s.space_id=ua.space_id
            WHERE a.account_id=#{accountId} AND a.deleted_at IS NULL
            """)
    ProfileRow find(long accountId);

    @Update("""
            UPDATE account SET username=#{username},email=#{email},email_verified=IF(email<=>#{email},email_verified,false),
              timezone=#{timezone},updated_at=UTC_TIMESTAMP(6)
            WHERE account_id=#{accountId} AND deleted_at IS NULL
            """)
    void update(@Param("accountId") long accountId, @Param("username") String username,
                @Param("email") String email, @Param("timezone") String timezone);

    @Insert("""
            INSERT INTO user_avatar(account_id,space_id,asset_id,updated_at)
            VALUES(#{accountId},#{spaceId},#{assetId},UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE space_id=VALUES(space_id),asset_id=VALUES(asset_id),updated_at=UTC_TIMESTAMP(6)
            """)
    void setAvatar(@Param("accountId") long accountId, @Param("spaceId") long spaceId,
                   @Param("assetId") long assetId);

    @Delete("DELETE FROM user_avatar WHERE account_id=#{accountId}")
    void clearAvatar(long accountId);

    @Update("""
            UPDATE account SET password_hash=#{passwordHash},token_version=token_version+1,updated_at=#{now}
            WHERE account_id=#{accountId} AND deleted_at IS NULL
            """)
    void updatePassword(@Param("accountId") long accountId, @Param("passwordHash") String passwordHash,
                        @Param("now") LocalDateTime now);

    @Update("UPDATE auth_session SET revoked_at=#{now} WHERE account_id=#{accountId} AND revoked_at IS NULL")
    void revokeSessions(@Param("accountId") long accountId, @Param("now") LocalDateTime now);

    final class ProfileRow {
        private long accountId;
        private byte[] publicId;
        private String username;
        private String passwordHash;
        private String email;
        private boolean emailVerified;
        private String systemRole;
        private String timezone;
        private byte[] avatarPublicId;
        private byte[] avatarSpacePublicId;

        public ProfileRow() {
        }

        public long accountId() { return accountId; }
        public byte[] publicId() { return publicId; }
        public String username() { return username; }
        public String passwordHash() { return passwordHash; }
        public String email() { return email; }
        public boolean emailVerified() { return emailVerified; }
        public String systemRole() { return systemRole; }
        public String timezone() { return timezone; }
        public byte[] avatarPublicId() { return avatarPublicId; }
        public byte[] avatarSpacePublicId() { return avatarSpacePublicId; }

        public void setAccountId(long accountId) { this.accountId = accountId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setUsername(String username) { this.username = username; }
        public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
        public void setEmail(String email) { this.email = email; }
        public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
        public void setSystemRole(String systemRole) { this.systemRole = systemRole; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
        public void setAvatarPublicId(byte[] avatarPublicId) { this.avatarPublicId = avatarPublicId; }
        public void setAvatarSpacePublicId(byte[] avatarSpacePublicId) { this.avatarSpacePublicId = avatarSpacePublicId; }
    }
}
