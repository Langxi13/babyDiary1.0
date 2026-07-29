package com.langxi.babydiary.migration.v3;

import java.sql.Connection;
import java.time.LocalDateTime;

final class IdentityDataMigration extends V3JdbcMigration {
    private static final String NAMESPACE = "6f589db1-7ae8-5f24-b517-fbdbd84eaf12";

    void migrate(Connection source, Connection target) throws Exception {
        accounts(source, target);
        spaces(source, target);
        members(source, target);
        accountTokens(source, target);
        recoveryCodes(source, target);
        systemInvitation(source, target);
        spaceInvitations(source, target);
    }

    private void accounts(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT user_id,username,email,email_verified,password,created_at,token_version,system_role,timezone FROM user ORDER BY user_id",
                "INSERT INTO account(account_id,public_id,username,password_hash,email,email_verified,system_role,timezone,token_version,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,'ACTIVE',?,?)",
                (row, insert) -> {
                    long id = row.getLong("user_id");
                    LocalDateTime createdAt = requiredTime(row, "created_at");
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.uuid(NAMESPACE, "account:" + id));
                    insert.setString(3, row.getString("username"));
                    insert.setString(4, row.getString("password"));
                    setString(insert, 5, row.getString("email"));
                    insert.setBoolean(6, row.getBoolean("email_verified"));
                    insert.setString(7, V3MigrationSupport.status(row.getString("system_role"), "USER"));
                    insert.setString(8, row.getString("timezone"));
                    insert.setInt(9, row.getInt("token_version"));
                    setTime(insert, 10, createdAt);
                    setTime(insert, 11, createdAt);
                });
    }

    private void spaces(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT space_id,public_id,name,type,created_by,personal_owner_id,default_visibility,storage_quota_bytes,created_at,updated_at FROM diary_space ORDER BY space_id",
                "INSERT INTO diary_space(space_id,public_id,name,type,created_by,personal_owner_id,default_visibility,storage_quota_bytes,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("space_id"));
                    insert.setBytes(2, V3MigrationSupport.parseUuid(row.getString("public_id"), NAMESPACE,
                            "space:" + row.getLong("space_id")));
                    insert.setString(3, row.getString("name"));
                    insert.setString(4, V3MigrationSupport.status(row.getString("type"), "PERSONAL"));
                    insert.setLong(5, row.getLong("created_by"));
                    setLong(insert, 6, row, "personal_owner_id");
                    insert.setString(7, V3MigrationSupport.status(row.getString("default_visibility"), "PRIVATE"));
                    insert.setLong(8, row.getLong("storage_quota_bytes"));
                    setTime(insert, 9, requiredTime(row, "created_at"));
                    setTime(insert, 10, requiredTime(row, "updated_at"));
                });
    }

    private void members(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT space_id,user_id,role,status,joined_at FROM space_member ORDER BY space_id,user_id",
                "INSERT INTO space_member(space_id,account_id,role,status,joined_at) VALUES(?,?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("space_id"));
                    insert.setLong(2, row.getLong("user_id"));
                    insert.setString(3, V3MigrationSupport.status(row.getString("role"), "MEMBER"));
                    insert.setString(4, V3MigrationSupport.status(row.getString("status"), "ACTIVE"));
                    setTime(insert, 5, requiredTime(row, "joined_at"));
                });
    }

    private void accountTokens(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT token_id,user_id,type,token_hash,expires_at,used_at,created_at FROM account_token ORDER BY token_id",
                "INSERT INTO account_token(token_id,account_id,type,token_hash,expires_at,used_at,created_at) VALUES(?,?,?,?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("token_id"));
                    insert.setLong(2, row.getLong("user_id"));
                    insert.setString(3, row.getString("type"));
                    insert.setBytes(4, V3MigrationSupport.hexBytes(row.getString("token_hash")));
                    setTime(insert, 5, requiredTime(row, "expires_at"));
                    setTime(insert, 6, V3MigrationSupport.utc(row, "used_at"));
                    setTime(insert, 7, requiredTime(row, "created_at"));
                });
    }

    private void recoveryCodes(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT recovery_code_id,user_id,code_hash,used_at,created_at FROM recovery_code ORDER BY recovery_code_id",
                "INSERT INTO recovery_code(recovery_code_id,account_id,code_hash,used_at,created_at) VALUES(?,?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("recovery_code_id"));
                    insert.setLong(2, row.getLong("user_id"));
                    insert.setBytes(3, V3MigrationSupport.hexBytes(row.getString("code_hash")));
                    setTime(insert, 4, V3MigrationSupport.utc(row, "used_at"));
                    setTime(insert, 5, requiredTime(row, "created_at"));
                });
    }

    private void systemInvitation(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT config_id,encrypted_code,updated_by,updated_at FROM system_invitation_config ORDER BY config_id",
                "INSERT INTO system_invitation_config(config_id,encrypted_code,updated_by,updated_at) VALUES(?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("config_id"));
                    insert.setString(2, row.getString("encrypted_code"));
                    setLong(insert, 3, row, "updated_by");
                    setTime(insert, 4, requiredTime(row, "updated_at"));
                });
    }

    private void spaceInvitations(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT invitation_id,space_id,invited_by,email,token_hash,role,status,expires_at,accepted_by,created_at FROM space_invitation ORDER BY invitation_id",
                "INSERT INTO space_invitation(invitation_id,public_id,space_id,invited_by,email,token_hash,role,status,expires_at,accepted_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("invitation_id");
                    String status = V3MigrationSupport.status(row.getString("status"), "PENDING");
                    if ("REJECTED".equals(status)) status = "REVOKED";
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.uuid(NAMESPACE, "space-invitation:" + id));
                    insert.setLong(3, row.getLong("space_id"));
                    insert.setLong(4, row.getLong("invited_by"));
                    setString(insert, 5, row.getString("email"));
                    insert.setBytes(6, V3MigrationSupport.hexBytes(row.getString("token_hash")));
                    insert.setString(7, V3MigrationSupport.status(row.getString("role"), "MEMBER"));
                    insert.setString(8, status);
                    setTime(insert, 9, requiredTime(row, "expires_at"));
                    setLong(insert, 10, row, "accepted_by");
                    setTime(insert, 11, requiredTime(row, "created_at"));
                });
    }
}
