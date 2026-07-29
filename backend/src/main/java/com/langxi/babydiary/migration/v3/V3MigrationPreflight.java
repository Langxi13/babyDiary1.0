package com.langxi.babydiary.migration.v3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class V3MigrationPreflight {
    private static final Set<String> REQUIRED_TABLES = Set.of(
            "user", "diary_space", "space_member", "diary", "tag", "diary_tag",
            "media_asset", "media_legacy_map", "diary_media", "album", "album_media",
            "anniversary", "ai_report", "ai_album_proposal", "flyway_schema_history");
    private static final List<String> COUNTED_TABLES = List.of(
            "user", "diary_space", "space_member", "diary", "tag", "diary_tag", "diary_revision",
            "diary_draft", "media_asset", "diary_media", "album_group", "album", "album_media",
            "favorite_media", "user_avatar", "anniversary", "ai_report", "ai_album_proposal",
            "notification", "reminder", "private_share");

    V3MigrationReport inspect(Connection source, Path objectRoot, boolean verifyObjects) throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        List<String> checks = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        Set<String> tables = tables(source);
        for (String required : REQUIRED_TABLES) {
            if (!tables.contains(required)) failures.add("Missing source table: " + required);
        }
        if (!failures.isEmpty()) return new V3MigrationReport("preflight", false, counts, checks, failures);

        String version = scalarString(source, "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1");
        if (!"15".equals(version)) failures.add("Source Flyway version must be 15, found " + version);
        else checks.add("source-flyway-v15");

        for (String table : COUNTED_TABLES) counts.put(table, scalarLong(source, "SELECT COUNT(*) FROM `" + table + "`"));

        requireZero(source, failures, "diary without a space", "SELECT COUNT(*) FROM diary WHERE space_id IS NULL");
        requireZero(source, failures, "media without a space", "SELECT COUNT(*) FROM media_asset WHERE space_id IS NULL");
        requireZero(source, failures, "unmapped legacy media", "SELECT COUNT(*) FROM media_asset a LEFT JOIN media_legacy_map m ON m.asset_id=a.asset_id WHERE m.asset_id IS NULL");
        requireZero(source, failures, "cross-space diary media", "SELECT COUNT(*) FROM diary_media dm JOIN diary d ON d.diary_id=dm.diary_id JOIN media_asset a ON a.asset_id=dm.asset_id WHERE d.space_id<>a.space_id");
        requireZero(source, failures, "cross-space album media", "SELECT COUNT(*) FROM album_media am JOIN album al ON al.album_id=am.album_id JOIN media_asset a ON a.asset_id=am.asset_id WHERE al.space_id<>a.space_id");
        requireZero(source, failures, "invalid UUID values", "SELECT "
                + "(SELECT COUNT(*) FROM diary_space WHERE public_id NOT REGEXP '^[0-9a-fA-F-]{36}$') + "
                + "(SELECT COUNT(*) FROM diary WHERE public_id NOT REGEXP '^[0-9a-fA-F-]{36}$') + "
                + "(SELECT COUNT(*) FROM media_asset WHERE public_id NOT REGEXP '^[0-9a-fA-F-]{36}$')");
        requireZero(source, failures, "invalid tag colors", "SELECT COUNT(*) FROM tag WHERE color IS NOT NULL AND color NOT REGEXP '^#[0-9A-Fa-f]{6}$'");

        long plain = scalarLong(source, "SELECT COUNT(*) FROM diary WHERE content_format='plain'");
        long html = scalarLong(source, "SELECT COUNT(*) FROM diary WHERE content_format='html'");
        if (plain + html != counts.get("diary")) failures.add("Unsupported diary content_format values exist");
        else checks.add("diary-content-formats");

        if (verifyObjects) verifyObjects(source, objectRoot, failures, checks);
        return new V3MigrationReport("preflight", failures.isEmpty(), counts, checks, failures);
    }

    private void verifyObjects(Connection source, Path objectRoot, List<String> failures, List<String> checks) throws SQLException {
        if (!Files.isDirectory(objectRoot)) {
            failures.add("Object root is not a directory: " + objectRoot);
            return;
        }
        try (Statement statement = source.createStatement(); ResultSet result = statement.executeQuery(
                "SELECT storage_provider,storage_key,thumbnail_key,poster_key,waveform_key,transcoded_key FROM media_asset WHERE deleted_at IS NULL")) {
            while (result.next()) {
                if (!"LOCAL".equalsIgnoreCase(result.getString("storage_provider"))) continue;
                for (String column : List.of("storage_key", "thumbnail_key", "poster_key", "waveform_key", "transcoded_key")) {
                    String key = result.getString(column);
                    if (key == null || key.isBlank()) continue;
                    Path file = objectRoot.resolve(key).normalize();
                    if (!file.startsWith(objectRoot) || !Files.isRegularFile(file)) failures.add("Missing media object for " + column + ": " + key);
                }
            }
        }
        if (failures.stream().noneMatch(item -> item.startsWith("Missing media object"))) checks.add("local-media-objects");
    }

    private Set<String> tables(Connection connection) throws SQLException {
        Set<String> result = new java.util.HashSet<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
            while (rows.next()) result.add(rows.getString(1));
        }
        return result;
    }

    private void requireZero(Connection source, List<String> failures, String label, String sql) throws SQLException {
        long count = scalarLong(source, sql);
        if (count != 0) failures.add(label + ": " + count);
    }

    private long scalarLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) throw new SQLException("Query returned no row: " + sql);
            return result.getLong(1);
        }
    }

    private String scalarString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) throw new SQLException("Query returned no row: " + sql);
            return result.getString(1);
        }
    }
}
