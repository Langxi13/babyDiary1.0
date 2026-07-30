package com.langxi.babydiary.identity.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AccountRecoveryMapper {
    @Select("SELECT account_id,email,email_verified FROM account WHERE account_id=#{accountId} AND deleted_at IS NULL")
    AccountEmailRow findEmailByAccount(long accountId);

    @Select("SELECT account_id,email,email_verified FROM account WHERE email=#{email} AND deleted_at IS NULL")
    AccountEmailRow findByEmail(String email);

    @Select("SELECT account_id,password_hash FROM account WHERE username=#{username} AND deleted_at IS NULL")
    AccountPasswordRow findByUsername(String username);

    @Update("UPDATE account SET email=#{email},email_verified=0,updated_at=UTC_TIMESTAMP(6) " +
            "WHERE account_id=#{accountId} AND deleted_at IS NULL")
    int updateEmail(@Param("accountId") long accountId, @Param("email") String email);

    @Insert("INSERT INTO account_token(account_id,type,token_hash,expires_at) " +
            "VALUES(#{accountId},#{type},#{tokenHash},#{expiresAt})")
    void insertToken(@Param("accountId") long accountId, @Param("type") String type,
                     @Param("tokenHash") byte[] tokenHash, @Param("expiresAt") LocalDateTime expiresAt);

    @Delete("DELETE FROM account_token WHERE account_id=#{accountId} AND type=#{type} AND used_at IS NULL")
    void deleteTokens(@Param("accountId") long accountId, @Param("type") String type);

    @Select("SELECT token_id,account_id FROM account_token WHERE token_hash=#{tokenHash} AND type=#{type} " +
            "AND used_at IS NULL AND expires_at>#{now} FOR UPDATE")
    TokenRow findTokenForUpdate(@Param("tokenHash") byte[] tokenHash, @Param("type") String type,
                                @Param("now") LocalDateTime now);

    @Update("UPDATE account_token SET used_at=#{now} WHERE token_id=#{tokenId} AND used_at IS NULL AND expires_at>#{now}")
    int consumeToken(@Param("tokenId") long tokenId, @Param("now") LocalDateTime now);

    @Update("UPDATE account SET email_verified=1,updated_at=UTC_TIMESTAMP(6) WHERE account_id=#{accountId}")
    void verifyEmail(long accountId);

    @Delete("DELETE FROM recovery_code WHERE account_id=#{accountId}")
    void deleteRecoveryCodes(long accountId);

    @Insert("<script>INSERT INTO recovery_code(account_id,code_hash) VALUES " +
            "<foreach collection='hashes' item='hash' separator=','>(#{accountId},#{hash})</foreach></script>")
    void insertRecoveryCodes(@Param("accountId") long accountId, @Param("hashes") List<byte[]> hashes);

    @Update("UPDATE recovery_code SET used_at=#{now} WHERE account_id=#{accountId} AND code_hash=#{codeHash} AND used_at IS NULL")
    int consumeRecoveryCode(@Param("accountId") long accountId, @Param("codeHash") byte[] codeHash,
                            @Param("now") LocalDateTime now);

    @Update("UPDATE account SET password_hash=#{passwordHash},token_version=token_version+1,updated_at=#{now} " +
            "WHERE account_id=#{accountId} AND deleted_at IS NULL")
    int updatePassword(@Param("accountId") long accountId, @Param("passwordHash") String passwordHash,
                       @Param("now") LocalDateTime now);

    @Update("UPDATE auth_session SET revoked_at=#{now} WHERE account_id=#{accountId} AND revoked_at IS NULL")
    void revokeSessions(@Param("accountId") long accountId, @Param("now") LocalDateTime now);

    record AccountEmailRow(long accountId, String email, boolean emailVerified) {}
    record AccountPasswordRow(long accountId, String passwordHash) {}
    record TokenRow(long tokenId, long accountId) {}
}
