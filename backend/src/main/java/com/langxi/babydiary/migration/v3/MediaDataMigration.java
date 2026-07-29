package com.langxi.babydiary.migration.v3;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

final class MediaDataMigration extends V3JdbcMigration {
    private static final String NAMESPACE = "6f589db1-7ae8-5f24-b517-fbdbd84eaf12";
    private final Path objectRoot;

    MediaDataMigration(Path objectRoot) {
        this.objectRoot = objectRoot;
    }

    void migrate(Connection source, Connection target) throws Exception {
        assets(source, target);
        variants(source, target);
        diaryMedia(source, target);
        favoriteMedia(source, target);
        avatars(source, target);
    }

    private void assets(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT asset_id,public_id,space_id,owner_user_id,media_type,original_filename,caption,ocr_text,taken_at,location_name,latitude,longitude,access_scope,library_visible,status,created_at,updated_at,deleted_at FROM media_asset ORDER BY asset_id",
                "INSERT INTO media_asset(asset_id,public_id,space_id,owner_id,media_type,original_filename,caption,ocr_text,taken_at,location_name,latitude,longitude,access_scope,library_visible,status,created_at,updated_at,deleted_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("asset_id");
                    String accessScope = V3MigrationSupport.status(row.getString("access_scope"), "LINKED");
                    if ("PROFILE".equals(accessScope)) accessScope = "LINKED";
                    String status = V3MigrationSupport.status(row.getString("status"), "READY");
                    if (!List.of("UPLOADING", "PROCESSING", "READY", "FAILED").contains(status)) status = "FAILED";
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.parseUuid(row.getString("public_id"), NAMESPACE, "media:" + id));
                    insert.setLong(3, row.getLong("space_id"));
                    insert.setLong(4, row.getLong("owner_user_id"));
                    insert.setString(5, V3MigrationSupport.status(row.getString("media_type"), "IMAGE"));
                    setString(insert, 6, row.getString("original_filename"));
                    setString(insert, 7, row.getString("caption"));
                    setString(insert, 8, row.getString("ocr_text"));
                    setTime(insert, 9, V3MigrationSupport.utc(row, "taken_at"));
                    setString(insert, 10, row.getString("location_name"));
                    insert.setBigDecimal(11, row.getBigDecimal("latitude"));
                    insert.setBigDecimal(12, row.getBigDecimal("longitude"));
                    insert.setString(13, accessScope);
                    insert.setBoolean(14, row.getBoolean("library_visible"));
                    insert.setString(15, status);
                    setTime(insert, 16, requiredTime(row, "created_at"));
                    setTime(insert, 17, requiredTime(row, "updated_at"));
                    setTime(insert, 18, V3MigrationSupport.utc(row, "deleted_at"));
                });
    }

    private void variants(Connection source, Connection target) throws Exception {
        try (PreparedStatement insert = target.prepareStatement(
                "INSERT INTO media_variant(asset_id,variant_type,profile,storage_provider,storage_key,content_type,size_bytes,checksum_sha256,width,height,duration_millis,status,processing_error,created_at,updated_at,deleted_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            each(source, "SELECT asset_id,storage_provider,storage_key,thumbnail_key,poster_key,waveform_key,transcoded_key,content_type,size_bytes,checksum_sha256,width,height,duration_seconds,status,processing_error,created_at,updated_at,deleted_at FROM media_asset ORDER BY asset_id", row -> {
                boolean required = V3MigrationSupport.utc(row, "deleted_at") == null;
                addVariant(insert, row, "ORIGINAL", "source", row.getString("storage_key"),
                        contentType(row.getString("content_type"), row.getString("storage_key")), row.getLong("size_bytes"),
                        V3MigrationSupport.hexBytes(row.getString("checksum_sha256")), nullableInt(row, "width"),
                        nullableInt(row, "height"), nullableLong(row, "duration_seconds"), required);
                addDerived(insert, row, "THUMBNAIL", "default", "thumbnail_key", required);
                addDerived(insert, row, "POSTER", "default", "poster_key", required);
                addDerived(insert, row, "WAVEFORM", "default", "waveform_key", required);
                addDerived(insert, row, "TRANSCODED", "default", "transcoded_key", required);
            });
            insert.executeBatch();
        }
    }

    private void addDerived(PreparedStatement insert, ResultSet row, String type, String profile,
                            String column, boolean required) throws Exception {
        String key = row.getString(column);
        if (key == null || key.isBlank()) return;
        String provider = row.getString("storage_provider");
        addVariant(insert, row, type, profile, key, contentType(null, key),
                V3MigrationSupport.fileSize(objectRoot, provider, key, required),
                V3MigrationSupport.fileSha256(objectRoot, provider, key, required),
                null, null, null, required);
    }

    private void addVariant(PreparedStatement insert, ResultSet row, String type, String profile,
                            String key, String contentType, long size, byte[] checksum, Integer width,
                            Integer height, Long durationSeconds, boolean required) throws Exception {
        String status = V3MigrationSupport.status(row.getString("status"), "READY");
        if (!"READY".equals(status) && !"FAILED".equals(status)) status = required ? "PENDING" : "FAILED";
        insert.setLong(1, row.getLong("asset_id"));
        insert.setString(2, type);
        insert.setString(3, profile);
        insert.setString(4, V3MigrationSupport.status(row.getString("storage_provider"), "LOCAL"));
        insert.setString(5, key);
        insert.setString(6, contentType);
        insert.setLong(7, Math.max(size, 0L));
        if (checksum == null) insert.setNull(8, Types.BINARY);
        else insert.setBytes(8, checksum);
        if (width == null) insert.setNull(9, Types.INTEGER); else insert.setInt(9, width);
        if (height == null) insert.setNull(10, Types.INTEGER); else insert.setInt(10, height);
        if (durationSeconds == null) insert.setNull(11, Types.BIGINT); else insert.setLong(11, durationSeconds * 1000L);
        insert.setString(12, status);
        setString(insert, 13, "FAILED".equals(status) ? row.getString("processing_error") : null);
        setTime(insert, 14, requiredTime(row, "created_at"));
        setTime(insert, 15, requiredTime(row, "updated_at"));
        setTime(insert, 16, V3MigrationSupport.utc(row, "deleted_at"));
        insert.addBatch();
    }

    private void diaryMedia(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT d.space_id,dm.diary_id,dm.asset_id,dm.sort,dm.created_at FROM diary_media dm JOIN diary d ON d.diary_id=dm.diary_id ORDER BY dm.diary_id,dm.sort,dm.asset_id",
                "INSERT INTO diary_media(space_id,diary_id,asset_id,position,created_at) VALUES(?,?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("space_id"));
                    insert.setLong(2, row.getLong("diary_id"));
                    insert.setLong(3, row.getLong("asset_id"));
                    insert.setInt(4, row.getInt("sort"));
                    setTime(insert, 5, requiredTime(row, "created_at"));
                });
    }

    private void favoriteMedia(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT a.space_id,f.user_id,f.asset_id,f.created_at FROM favorite_media f JOIN media_asset a ON a.asset_id=f.asset_id ORDER BY f.user_id,f.asset_id",
                "INSERT INTO favorite_media(space_id,account_id,asset_id,created_at) VALUES(?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("space_id"));
                    insert.setLong(2, row.getLong("user_id"));
                    insert.setLong(3, row.getLong("asset_id"));
                    setTime(insert, 4, requiredTime(row, "created_at"));
                });
    }

    private void avatars(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT a.space_id,v.user_id,v.asset_id,v.updated_at FROM user_avatar v JOIN media_asset a ON a.asset_id=v.asset_id ORDER BY v.user_id",
                "INSERT INTO user_avatar(account_id,space_id,asset_id,updated_at) VALUES(?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("user_id"));
                    insert.setLong(2, row.getLong("space_id"));
                    insert.setLong(3, row.getLong("asset_id"));
                    setTime(insert, 4, requiredTime(row, "updated_at"));
                });
    }

    private Integer nullableInt(ResultSet result, String column) throws Exception {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet result, String column) throws Exception {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private String contentType(String value, String key) {
        if (value != null && !value.isBlank()) return value;
        String normalized = key == null ? "" : key.toLowerCase();
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) return "image/jpeg";
        if (normalized.endsWith(".png")) return "image/png";
        if (normalized.endsWith(".webp")) return "image/webp";
        if (normalized.endsWith(".mp4")) return "video/mp4";
        if (normalized.endsWith(".mp3")) return "audio/mpeg";
        if (normalized.endsWith(".wav")) return "audio/wav";
        return "application/octet-stream";
    }
}
