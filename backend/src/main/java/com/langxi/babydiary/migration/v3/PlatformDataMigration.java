package com.langxi.babydiary.migration.v3;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.time.LocalDateTime;

final class PlatformDataMigration extends V3JdbcMigration {
    private static final String NAMESPACE = "6f589db1-7ae8-5f24-b517-fbdbd84eaf12";

    void migrate(Connection source, Connection target) throws Exception {
        notifications(source, target);
        pushSubscriptions(source, target);
        reminders(source, target);
        privateShares(source, target);
        syncChanges(source, target);
        syncOperations(source, target);
        storageUsage(source, target);
    }

    private void notifications(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT notification_id,public_id,user_id,space_id,type,title,body,target_path,dedupe_key,read_at,created_at FROM notification ORDER BY notification_id",
                "INSERT INTO notification(notification_id,public_id,account_id,space_id,type,title,body,target_ref,dedupe_key,read_at,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("notification_id");
                    String path = row.getString("target_path");
                    ObjectNode targetRef = null;
                    if (path != null && !path.isBlank()) {
                        targetRef = V3MigrationSupport.object();
                        targetRef.put("legacyPath", path);
                    }
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.parseUuid(row.getString("public_id"), NAMESPACE, "notification:" + id));
                    insert.setLong(3, row.getLong("user_id"));
                    setLong(insert, 4, row, "space_id");
                    insert.setString(5, row.getString("type"));
                    insert.setString(6, row.getString("title"));
                    setString(insert, 7, row.getString("body"));
                    setString(insert, 8, targetRef == null ? null : V3MigrationSupport.stringify(targetRef));
                    setString(insert, 9, row.getString("dedupe_key"));
                    setTime(insert, 10, V3MigrationSupport.utc(row, "read_at"));
                    setTime(insert, 11, requiredTime(row, "created_at"));
                });
    }

    private void pushSubscriptions(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT subscription_id,user_id,endpoint_hash,endpoint,p256dh,auth_secret,user_agent,created_at,last_success_at,revoked_at FROM push_subscription ORDER BY subscription_id",
                "INSERT INTO push_subscription(subscription_id,account_id,endpoint_hash,endpoint,p256dh,auth_secret,user_agent,created_at,last_success_at,revoked_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("subscription_id"));
                    insert.setLong(2, row.getLong("user_id"));
                    insert.setBytes(3, V3MigrationSupport.hexBytes(row.getString("endpoint_hash")));
                    insert.setString(4, row.getString("endpoint"));
                    insert.setString(5, row.getString("p256dh"));
                    insert.setString(6, row.getString("auth_secret"));
                    setString(insert, 7, row.getString("user_agent"));
                    setTime(insert, 8, requiredTime(row, "created_at"));
                    setTime(insert, 9, V3MigrationSupport.utc(row, "last_success_at"));
                    setTime(insert, 10, V3MigrationSupport.utc(row, "revoked_at"));
                });
    }

    private void reminders(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT reminder_id,public_id,user_id,space_id,type,enabled,schedule_json,next_run_at,last_run_at,created_at,updated_at FROM reminder ORDER BY reminder_id",
                "INSERT INTO reminder(reminder_id,public_id,account_id,space_id,type,enabled,schedule,next_run_at,last_run_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("reminder_id");
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.parseUuid(row.getString("public_id"), NAMESPACE, "reminder:" + id));
                    insert.setLong(3, row.getLong("user_id"));
                    setLong(insert, 4, row, "space_id");
                    insert.setString(5, row.getString("type"));
                    insert.setBoolean(6, row.getBoolean("enabled"));
                    insert.setString(7, V3MigrationSupport.jsonOrEmptyObject(row.getString("schedule_json")));
                    setTime(insert, 8, V3MigrationSupport.utc(row, "next_run_at"));
                    setTime(insert, 9, V3MigrationSupport.utc(row, "last_run_at"));
                    setTime(insert, 10, requiredTime(row, "created_at"));
                    setTime(insert, 11, requiredTime(row, "updated_at"));
                });
    }

    private void privateShares(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT share_id,public_id,token_hash,space_id,diary_id,created_by,password_hash,expires_at,max_views,view_count,revoked_at,created_at FROM private_share ORDER BY share_id",
                "INSERT INTO private_share(share_id,public_id,token_hash,space_id,diary_id,created_by,password_hash,expires_at,max_views,view_count,revoked_at,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("share_id");
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.parseUuid(row.getString("public_id"), NAMESPACE, "private-share:" + id));
                    insert.setBytes(3, V3MigrationSupport.hexBytes(row.getString("token_hash")));
                    insert.setLong(4, row.getLong("space_id"));
                    insert.setLong(5, row.getLong("diary_id"));
                    insert.setLong(6, row.getLong("created_by"));
                    setString(insert, 7, row.getString("password_hash"));
                    setTime(insert, 8, requiredTime(row, "expires_at"));
                    setInt(insert, 9, row, "max_views");
                    insert.setInt(10, row.getInt("view_count"));
                    setTime(insert, 11, V3MigrationSupport.utc(row, "revoked_at"));
                    setTime(insert, 12, requiredTime(row, "created_at"));
                });
    }

    private void syncChanges(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT change_seq,space_id,entity_type,entity_public_id,operation,revision,actor_user_id,created_at FROM sync_change ORDER BY change_seq",
                "INSERT INTO sync_change(change_seq,space_id,entity_type,entity_public_id,operation,revision,actor_id,created_at) VALUES(?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("change_seq");
                    String operation = "DELETE".equalsIgnoreCase(row.getString("operation")) ? "DELETE" : "UPSERT";
                    insert.setLong(1, id);
                    insert.setLong(2, row.getLong("space_id"));
                    insert.setString(3, row.getString("entity_type"));
                    insert.setBytes(4, V3MigrationSupport.parseUuid(row.getString("entity_public_id"), NAMESPACE, "sync-change:" + id));
                    insert.setString(5, operation);
                    insert.setInt(6, Math.max(row.getInt("revision"), 1));
                    insert.setLong(7, row.getLong("actor_user_id"));
                    setTime(insert, 8, requiredTime(row, "created_at"));
                });
    }

    private void syncOperations(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT operation_id,user_id,space_id,created_at FROM sync_operation ORDER BY created_at,operation_id",
                "INSERT INTO sync_operation(operation_id,account_id,space_id,result_code,created_at,expires_at) VALUES(?,?,?,'MIGRATED',?,?)",
                (row, insert) -> {
                    LocalDateTime createdAt = requiredTime(row, "created_at");
                    insert.setBytes(1, V3MigrationSupport.parseUuid(row.getString("operation_id"), NAMESPACE,
                            "sync-operation:" + row.getString("operation_id")));
                    insert.setLong(2, row.getLong("user_id"));
                    insert.setLong(3, row.getLong("space_id"));
                    setTime(insert, 4, createdAt);
                    setTime(insert, 5, createdAt.plusDays(7));
                });
    }

    private void storageUsage(Connection source, Connection target) throws Exception {
        try (var statement = target.createStatement()) {
            statement.executeUpdate("INSERT INTO space_storage_usage(space_id,used_bytes) "
                    + "SELECT s.space_id,COALESCE(SUM(CASE WHEN v.deleted_at IS NULL THEN v.size_bytes ELSE 0 END),0) "
                    + "FROM diary_space s LEFT JOIN media_asset a ON a.space_id=s.space_id AND a.deleted_at IS NULL "
                    + "LEFT JOIN media_variant v ON v.asset_id=a.asset_id GROUP BY s.space_id");
        }
        try (var update = target.prepareStatement(
                "UPDATE space_storage_usage SET updated_at=? WHERE space_id=?")) {
            each(source, "SELECT space_id,updated_at FROM space_storage_usage ORDER BY space_id", row -> {
                setTime(update, 1, requiredTime(row, "updated_at"));
                update.setLong(2, row.getLong("space_id"));
                update.addBatch();
            });
            update.executeBatch();
        }
    }
}
