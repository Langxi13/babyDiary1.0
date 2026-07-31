package com.langxi.babydiary.identity.infrastructure;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CredentialMapper {
    @Select(
            "SELECT password_hash FROM account WHERE account_id=#{accountId} AND deleted_at IS NULL")
    String findPasswordHash(long accountId);

    @Update(
            """
            UPDATE account SET password_hash=#{passwordHash},token_version=token_version+1,updated_at=#{now}
            WHERE account_id=#{accountId} AND deleted_at IS NULL
            """)
    void updatePassword(
            @Param("accountId") long accountId,
            @Param("passwordHash") String passwordHash,
            @Param("now") LocalDateTime now);

    @Update(
            "UPDATE auth_session SET revoked_at=#{now} WHERE account_id=#{accountId} AND revoked_at IS NULL")
    void revokeSessions(@Param("accountId") long accountId, @Param("now") LocalDateTime now);
}
