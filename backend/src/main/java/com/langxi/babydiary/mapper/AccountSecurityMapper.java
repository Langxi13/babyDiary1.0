package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.AccountToken;
import com.langxi.babydiary.entity.AuthSession;
import org.apache.ibatis.annotations.*;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface AccountSecurityMapper {
    @Insert("INSERT INTO auth_session(public_id,user_id,refresh_token_hash,device_name,user_agent,ip_address,expires_at) " +
            "VALUES(#{publicId},#{userId},#{refreshTokenHash},#{deviceName},#{userAgent},#{ipAddress},#{expiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "sessionId")
    void insertSession(AuthSession session);

    @Select("SELECT session_id sessionId,public_id publicId,user_id userId,refresh_token_hash refreshTokenHash," +
            "device_name deviceName,user_agent userAgent,ip_address ipAddress,expires_at expiresAt," +
            "last_seen_at lastSeenAt,revoked_at revokedAt,created_at createdAt " +
            "FROM auth_session WHERE refresh_token_hash=#{tokenHash} FOR UPDATE")
    AuthSession findSessionByTokenForUpdate(@Param("tokenHash") String tokenHash);

    @Select("SELECT session_id sessionId,public_id publicId,user_id userId,refresh_token_hash refreshTokenHash," +
            "device_name deviceName,user_agent userAgent,ip_address ipAddress,expires_at expiresAt," +
            "last_seen_at lastSeenAt,revoked_at revokedAt,created_at createdAt " +
            "FROM auth_session WHERE refresh_token_hash=#{tokenHash}")
    AuthSession findSessionByToken(@Param("tokenHash") String tokenHash);

    @Update("UPDATE auth_session SET refresh_token_hash=#{nextHash},last_seen_at=NOW(),expires_at=#{expiresAt} " +
            "WHERE session_id=#{sessionId} AND refresh_token_hash=#{previousHash} AND revoked_at IS NULL")
    int rotateSession(@Param("sessionId") Long sessionId,
                      @Param("previousHash") String previousHash,
                      @Param("nextHash") String nextHash,
                      @Param("expiresAt") Timestamp expiresAt);

    @Update("UPDATE auth_session SET revoked_at=NOW() WHERE refresh_token_hash=#{tokenHash} AND revoked_at IS NULL")
    int revokeByToken(@Param("tokenHash") String tokenHash);

    @Update("UPDATE auth_session SET revoked_at=NOW() WHERE user_id=#{userId} AND public_id=#{publicId} AND revoked_at IS NULL")
    int revokeSession(@Param("userId") Integer userId, @Param("publicId") String publicId);

    @Update("UPDATE auth_session SET revoked_at=NOW() WHERE user_id=#{userId} AND revoked_at IS NULL")
    int revokeAllSessions(@Param("userId") Integer userId);

    @Select("SELECT session_id sessionId,public_id publicId,user_id userId,device_name deviceName,user_agent userAgent," +
            "ip_address ipAddress,expires_at expiresAt,last_seen_at lastSeenAt,revoked_at revokedAt,created_at createdAt " +
            "FROM auth_session WHERE user_id=#{userId} AND revoked_at IS NULL AND expires_at>NOW() ORDER BY last_seen_at DESC")
    List<AuthSession> listActiveSessions(@Param("userId") Integer userId);

    @Insert("INSERT INTO account_token(user_id,type,token_hash,expires_at) VALUES(#{userId},#{type},#{tokenHash},#{expiresAt})")
    @Options(useGeneratedKeys = true, keyProperty = "tokenId")
    void insertAccountToken(AccountToken token);

    @Select("SELECT token_id tokenId,user_id userId,type,token_hash tokenHash,expires_at expiresAt,used_at usedAt,created_at createdAt " +
            "FROM account_token WHERE token_hash=#{tokenHash} AND type=#{type} AND used_at IS NULL AND expires_at>NOW()")
    AccountToken findValidAccountToken(@Param("tokenHash") String tokenHash, @Param("type") String type);

    @Select("SELECT token_id tokenId,user_id userId,type,token_hash tokenHash,expires_at expiresAt,used_at usedAt,created_at createdAt " +
            "FROM account_token WHERE token_hash=#{tokenHash} AND type=#{type} AND used_at IS NULL AND expires_at>NOW() FOR UPDATE")
    AccountToken findValidAccountTokenForUpdate(@Param("tokenHash") String tokenHash, @Param("type") String type);

    @Update("UPDATE account_token SET used_at=NOW() WHERE token_id=#{tokenId} AND used_at IS NULL AND expires_at>NOW()")
    int consumeAccountToken(@Param("tokenId") Long tokenId);

    @Delete("DELETE FROM account_token WHERE user_id=#{userId} AND type=#{type}")
    int deleteAccountTokens(@Param("userId") Integer userId, @Param("type") String type);

    @Delete("DELETE FROM recovery_code WHERE user_id=#{userId}")
    int deleteRecoveryCodes(@Param("userId") Integer userId);

    @Insert("<script>INSERT INTO recovery_code(user_id,code_hash) VALUES " +
            "<foreach collection='hashes' item='hash' separator=','>(#{userId},#{hash})</foreach></script>")
    void insertRecoveryCodes(@Param("userId") Integer userId, @Param("hashes") List<String> hashes);

    @Update("UPDATE recovery_code SET used_at=NOW() WHERE user_id=#{userId} AND code_hash=#{codeHash} AND used_at IS NULL")
    int consumeRecoveryCode(@Param("userId") Integer userId, @Param("codeHash") String codeHash);

    @Delete("DELETE FROM auth_session WHERE expires_at<DATE_SUB(NOW(),INTERVAL 30 DAY) " +
            "OR (revoked_at IS NOT NULL AND revoked_at<DATE_SUB(NOW(),INTERVAL 30 DAY))")
    int purgeOldSessions();

    @Delete("DELETE FROM account_token WHERE expires_at<NOW() OR used_at IS NOT NULL")
    int purgeExpiredAccountTokens();
}
