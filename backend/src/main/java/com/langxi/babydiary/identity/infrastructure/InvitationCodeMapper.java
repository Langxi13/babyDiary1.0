package com.langxi.babydiary.identity.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InvitationCodeMapper {
    @Select("SELECT encrypted_code FROM system_invitation_config WHERE config_id=1")
    String findEncrypted();

    @Select("SELECT encrypted_code FROM system_invitation_config WHERE config_id=1 FOR UPDATE")
    String findEncryptedForUpdate();

    @Insert("""
            INSERT INTO system_invitation_config(config_id,encrypted_code,updated_by,updated_at)
            VALUES(1,#{encryptedCode},#{updatedBy},UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE encrypted_code=VALUES(encrypted_code),updated_by=VALUES(updated_by),updated_at=UTC_TIMESTAMP(6)
            """)
    void upsert(@Param("encryptedCode") String encryptedCode, @Param("updatedBy") Long updatedBy);
}
