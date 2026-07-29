package com.langxi.babydiary.v3.identity.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Options;

import java.time.LocalDateTime;

@Mapper
public interface IdentityMapper {
    @Select("""
            SELECT account_id,public_id,username,password_hash,email,email_verified,system_role,
                   timezone,token_version,status
            FROM account WHERE username=#{username} AND deleted_at IS NULL
            """)
    AccountRow findByUsername(String username);

    @Select("""
            SELECT account_id,public_id,username,password_hash,email,email_verified,system_role,
                   timezone,token_version,status
            FROM account WHERE account_id=#{accountId} AND deleted_at IS NULL
            """)
    AccountRow findById(long accountId);

    @Insert("INSERT INTO account(public_id,username,password_hash,system_role,timezone,status,created_at,updated_at) " +
            "VALUES(#{publicId},#{username},#{passwordHash},'USER','Asia/Shanghai','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")
    @Options(useGeneratedKeys = true, keyProperty = "accountId")
    void insertAccount(AccountInsert row);

    @Select("SELECT COUNT(*) FROM account")
    long countAccounts();

    @Update("UPDATE account SET system_role='ADMIN',updated_at=UTC_TIMESTAMP(6) WHERE account_id=#{accountId}")
    void promoteToAdmin(long accountId);

    @Select("SELECT public_id,device_name,user_agent,ip_address,expires_at,last_seen_at,created_at FROM auth_session " +
            "WHERE account_id=#{accountId} AND revoked_at IS NULL AND expires_at>UTC_TIMESTAMP(6) ORDER BY last_seen_at DESC")
    java.util.List<SessionRow> findSessions(long accountId);

    @Select("SELECT BIN_TO_UUID(public_id) FROM auth_session WHERE account_id=#{accountId} AND refresh_token_hash=#{refreshHash} " +
            "AND revoked_at IS NULL AND expires_at>#{now}")
    String findSessionId(@Param("refreshHash") byte[] refreshHash, @Param("accountId") long accountId,
                         @Param("now") LocalDateTime now);

    @Update("UPDATE auth_session SET revoked_at=#{now} WHERE account_id=#{accountId} AND public_id=#{publicId} AND revoked_at IS NULL")
    int revokeSession(@Param("accountId") long accountId, @Param("publicId") byte[] publicId,
                      @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO auth_session(public_id,account_id,refresh_token_hash,device_name,user_agent,
              ip_address,expires_at,last_seen_at,created_at)
            VALUES(#{publicId},#{accountId},#{refreshHash},#{deviceName},#{userAgent},#{ipAddress},
              #{expiresAt},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    void insertSession(@Param("publicId") byte[] publicId,
                       @Param("accountId") long accountId,
                       @Param("refreshHash") byte[] refreshHash,
                       @Param("deviceName") String deviceName,
                       @Param("userAgent") String userAgent,
                       @Param("ipAddress") String ipAddress,
                       @Param("expiresAt") LocalDateTime expiresAt);

    @Select("""
            SELECT s.session_id,s.expires_at,a.account_id,a.public_id,a.username,a.password_hash,a.email,
                   a.email_verified,a.system_role,a.timezone,a.token_version,a.status
            FROM auth_session s JOIN account a ON a.account_id=s.account_id
            WHERE s.refresh_token_hash=#{refreshHash} AND s.revoked_at IS NULL AND s.expires_at>#{now}
              AND a.deleted_at IS NULL
            """)
    RefreshSessionRow findRefreshSession(@Param("refreshHash") byte[] refreshHash,
                                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE auth_session SET refresh_token_hash=#{nextHash},last_seen_at=#{now}
            WHERE session_id=#{sessionId} AND refresh_token_hash=#{previousHash}
              AND revoked_at IS NULL AND expires_at>#{now}
            """)
    int rotateRefreshToken(@Param("sessionId") long sessionId,
                           @Param("previousHash") byte[] previousHash,
                           @Param("nextHash") byte[] nextHash,
                           @Param("now") LocalDateTime now);

    @Update("""
            UPDATE auth_session SET revoked_at=#{now}
            WHERE refresh_token_hash=#{refreshHash} AND revoked_at IS NULL
            """)
    void revokeRefreshToken(@Param("refreshHash") byte[] refreshHash,
                            @Param("now") LocalDateTime now);

    record AccountRow(long accountId, byte[] publicId, String username, String passwordHash,
                      String email, boolean emailVerified, String systemRole, String timezone,
                      int tokenVersion, String status) {
    }

    record RefreshSessionRow(long sessionId, LocalDateTime expiresAt, long accountId, byte[] publicId,
                             String username, String passwordHash, String email, boolean emailVerified,
                             String systemRole, String timezone, int tokenVersion, String status) {
    }

    final class AccountInsert {
        private Long accountId;
        private final byte[] publicId;
        private final String username;
        private final String passwordHash;

        public AccountInsert(byte[] publicId, String username, String passwordHash) {
            this.publicId = publicId;
            this.username = username;
            this.passwordHash = passwordHash;
        }
        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public byte[] getPublicId() { return publicId; }
        public String getUsername() { return username; }
        public String getPasswordHash() { return passwordHash; }
    }

    record SessionRow(byte[] publicId, String deviceName, String userAgent, String ipAddress,
                      LocalDateTime expiresAt, LocalDateTime lastSeenAt, LocalDateTime createdAt) {
    }
}
