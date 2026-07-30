package com.langxi.babydiary.migration.v3;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class V3MigrationVerifier {
    private static final Map<String, String> DIRECT_COUNTS = Map.ofEntries(
            Map.entry("account", "user"),
            Map.entry("diary_space", "diary_space"),
            Map.entry("space_member", "space_member"),
            Map.entry("tag", "tag"),
            Map.entry("diary", "diary"),
            Map.entry("diary_tag", "diary_tag"),
            Map.entry("diary_draft", "diary_draft"),
            Map.entry("diary_comment", "diary_comment"),
            Map.entry("diary_reaction", "diary_reaction"),
            Map.entry("diary_template", "diary_template"),
            Map.entry("media_asset", "media_asset"),
            Map.entry("diary_media", "diary_media"),
            Map.entry("album_group", "album_group"),
            Map.entry("album", "album"),
            Map.entry("album_media", "album_media"),
            Map.entry("favorite_media", "favorite_media"),
            Map.entry("user_avatar", "user_avatar"),
            Map.entry("anniversary", "anniversary"),
            Map.entry("ai_config", "ai_config"),
            Map.entry("ai_report", "ai_report"),
            Map.entry("ai_album_proposal", "ai_album_proposal"),
            Map.entry("space_ai_schedule", "space_ai_schedule"),
            Map.entry("notification", "notification"),
            Map.entry("push_subscription", "push_subscription"),
            Map.entry("reminder", "reminder"),
            Map.entry("private_share", "private_share"),
            Map.entry("sync_change", "sync_change"),
            Map.entry("sync_operation", "sync_operation")
    );

    V3MigrationReport verify(Connection source, Connection target) throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>();
        List<String> checks = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, String> mapping : DIRECT_COUNTS.entrySet()) {
            long expected = scalar(source, "SELECT COUNT(*) FROM `" + mapping.getValue() + "`");
            long actual = scalar(target, "SELECT COUNT(*) FROM `" + mapping.getKey() + "`");
            counts.put(mapping.getKey(), actual);
            if (expected != actual) failures.add("Count mismatch for " + mapping.getKey() + ": expected=" + expected + ", actual=" + actual);
        }

        compareDerivedCount(source, target, failures, counts, "diary_revision",
                "SELECT COUNT(*) + (SELECT COUNT(*) FROM diary d WHERE NOT EXISTS (SELECT 1 FROM diary_revision r WHERE r.diary_id=d.diary_id AND r.version=d.version)) FROM diary_revision",
                "SELECT COUNT(*) FROM diary_revision");
        compareDerivedCount(source, target, failures, counts, "media_variant",
                "SELECT COALESCE(SUM(1 + (thumbnail_key IS NOT NULL) + (poster_key IS NOT NULL) + (waveform_key IS NOT NULL) + (transcoded_key IS NOT NULL)),0) FROM media_asset",
                "SELECT COUNT(*) FROM media_variant");
        compareDerivedCount(source, target, failures, counts, "ai_album_candidate",
                "SELECT COALESCE(SUM(JSON_LENGTH(content_json, '$.albums')),0) FROM ai_album_proposal",
                "SELECT COUNT(*) FROM ai_album_candidate");
        compareDerivedCount(source, target, failures, counts, "ai_report_diary",
                "SELECT COUNT(*) FROM ai_report r JOIN diary d ON d.space_id=r.space_id AND d.date BETWEEN r.period_start AND r.period_end AND d.deleted_at IS NULL",
                "SELECT COUNT(*) FROM ai_report_diary");

        if (scalar(target, "SELECT COUNT(*) FROM auth_session") != 0) failures.add("Target auth sessions must be empty");
        if (scalar(target, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('user','diary_image','album_photo','favorite_photo','media_legacy_map','search_document')") != 0) {
            failures.add("Legacy tables exist in V3 target");
        }
        if (scalar(target, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND COLLATION_NAME IN ('utf8mb4_general_ci','utf8mb4_unicode_ci')") != 0) {
            failures.add("Legacy collations exist in V3 target");
        }
        if (scalar(target, "SELECT COUNT(*) FROM diary_media dm JOIN diary d ON d.diary_id=dm.diary_id JOIN media_asset a ON a.asset_id=dm.asset_id WHERE d.space_id<>a.space_id") != 0) {
            failures.add("Cross-space diary media exists in V3 target");
        }
        if (scalar(target, "SELECT COUNT(*) FROM media_asset a WHERE a.deleted_at IS NULL AND a.status='READY' "
                + "AND NOT EXISTS (SELECT 1 FROM media_variant v WHERE v.asset_id=a.asset_id "
                + "AND v.variant_type='ORIGINAL' AND v.status='READY' AND v.deleted_at IS NULL)") != 0) {
            failures.add("Ready media without a readable original variant exists in V3 target");
        }
        if (scalar(target, "SELECT COUNT(*) FROM space_storage_usage u JOIN (SELECT s.space_id,COALESCE(SUM(v.size_bytes),0) bytes FROM diary_space s LEFT JOIN media_asset a ON a.space_id=s.space_id AND a.deleted_at IS NULL LEFT JOIN media_variant v ON v.asset_id=a.asset_id AND v.deleted_at IS NULL GROUP BY s.space_id) c ON c.space_id=u.space_id WHERE c.bytes<>u.used_bytes") != 0) {
            failures.add("Storage usage does not equal media variant bytes");
        }

        compareAccounts(source, target, failures);
        compareDiaries(source, target, failures);
        compareMedia(source, target, failures);
        compareSimple(source, target, failures, "album",
                "SELECT album_id,space_id,user_id,name,COALESCE(description,''),type,COALESCE(cover_asset_id,0),sort FROM album ORDER BY album_id",
                "SELECT album_id,space_id,created_by,name,COALESCE(description,''),type,COALESCE(cover_asset_id,0),sort_order FROM album ORDER BY album_id");
        compareSimple(source, target, failures, "anniversary",
                "SELECT anniversary_id,space_id,user_id,title,date,COALESCE(description,''),COALESCE(cover_asset_id,0),sort FROM anniversary ORDER BY anniversary_id",
                "SELECT anniversary_id,space_id,created_by,title,anniversary_date,COALESCE(description,''),COALESCE(cover_asset_id,0),sort_order FROM anniversary ORDER BY anniversary_id");
        compareSimple(source, target, failures, "ai_report",
                "SELECT report_id,space_id,user_id,type,period_start,period_end,title,content_markdown,diary_count,COALESCE(model,'') FROM ai_report ORDER BY report_id",
                "SELECT report_id,space_id,created_by,period_type,period_start,period_end,title,content_markdown,diary_count,COALESCE(model,'') FROM ai_report ORDER BY report_id");

        if (failures.isEmpty()) checks.addAll(List.of(
                "all-counts", "account-semantics", "diary-semantics", "media-semantics",
                "media-variant-availability", "memory-semantics", "ai-report-semantics", "space-isolation",
                "storage-accounting", "legacy-removed"));
        return new V3MigrationReport("verify", failures.isEmpty(), counts, checks, failures);
    }

    private void compareAccounts(Connection source, Connection target, List<String> failures) throws Exception {
        compare(source, target,
                "SELECT user_id,username,password,COALESCE(email,''),email_verified,system_role,timezone,token_version FROM user ORDER BY user_id",
                "SELECT account_id,username,password_hash,COALESCE(email,''),email_verified,system_role,timezone,token_version FROM account ORDER BY account_id",
                "account", failures, (left, right) -> equalColumns(left, right, 8));
    }

    private void compareDiaries(Connection source, Connection target, List<String> failures) throws Exception {
        compare(source, target,
                "SELECT diary_id,space_id,user_id,title,date,content,content_format,COALESCE(mood_key,''),visibility,locked,version,deleted_at FROM diary ORDER BY diary_id",
                "SELECT diary_id,space_id,author_id,title,diary_date,content_html,content_text,COALESCE(mood_key,''),visibility,locked,version,deleted_at FROM diary ORDER BY diary_id",
                "diary", failures, (left, right) -> {
                    if (!equalColumns(left, right, 5)) return false;
                    String content = left.getString(6);
                    String format = left.getString(7);
                    String html = V3MigrationSupport.contentHtml(content, format);
                    if (!Objects.equals(html, right.getString(6))) return false;
                    if (!Objects.equals(V3MigrationSupport.contentText(content, format, html), right.getString(7))) return false;
                    for (int index = 8; index <= 11; index++) {
                        if (!valuesEqual(left.getObject(index), right.getObject(index))) return false;
                    }
                    return Objects.equals(V3MigrationSupport.utc(left, "deleted_at"),
                            V3MigrationSupport.targetUtc(right, 12));
                });
    }

    private void compareMedia(Connection source, Connection target, List<String> failures) throws Exception {
        compare(source, target,
                "SELECT asset_id,space_id,owner_user_id,media_type,storage_provider,storage_key,size_bytes,COALESCE(checksum_sha256,''),status,deleted_at FROM media_asset ORDER BY asset_id",
                "SELECT a.asset_id,a.space_id,a.owner_id,a.media_type,v.storage_provider,v.storage_key,v.size_bytes,COALESCE(HEX(v.checksum_sha256),''),a.status,a.deleted_at FROM media_asset a JOIN media_variant v ON v.asset_id=a.asset_id AND v.variant_type='ORIGINAL' ORDER BY a.asset_id",
                "media", failures, (left, right) -> {
                    for (int index = 1; index <= 9; index++) {
                        Object leftValue = left.getObject(index);
                        Object rightValue = right.getObject(index);
                        if (index == 8) {
                            leftValue = left.getString(index).toUpperCase();
                            rightValue = right.getString(index).toUpperCase();
                        }
                        if (!valuesEqual(leftValue, rightValue)) return false;
                    }
                    return Objects.equals(V3MigrationSupport.utc(left, "deleted_at"),
                            V3MigrationSupport.targetUtc(right, 10));
                });
    }

    private void compareSimple(Connection source, Connection target, List<String> failures,
                               String name, String sourceSql, String targetSql) throws Exception {
        compare(source, target, sourceSql, targetSql, name, failures,
                (left, right) -> equalColumns(left, right, left.getMetaData().getColumnCount()));
    }

    private void compare(Connection source, Connection target, String sourceSql, String targetSql,
                         String name, List<String> failures, RowEquality equality) throws Exception {
        try (Statement sourceStatement = source.createStatement(); Statement targetStatement = target.createStatement();
             ResultSet left = sourceStatement.executeQuery(sourceSql); ResultSet right = targetStatement.executeQuery(targetSql)) {
            long row = 0;
            while (true) {
                boolean leftNext = left.next();
                boolean rightNext = right.next();
                if (leftNext != rightNext) {
                    failures.add("Semantic row count mismatch for " + name);
                    return;
                }
                if (!leftNext) return;
                row++;
                if (!equality.equal(left, right)) {
                    failures.add("Semantic mismatch for " + name + " row " + row + " id=" + left.getObject(1));
                    return;
                }
            }
        }
    }

    private boolean equalColumns(ResultSet left, ResultSet right, int count) throws SQLException {
        for (int index = 1; index <= count; index++) {
            if (!valuesEqual(left.getObject(index), right.getObject(index))) return false;
        }
        return true;
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return new java.math.BigDecimal(leftNumber.toString())
                    .compareTo(new java.math.BigDecimal(rightNumber.toString())) == 0;
        }
        if (left instanceof byte[] leftBytes && right instanceof byte[] rightBytes) {
            return java.util.Arrays.equals(leftBytes, rightBytes);
        }
        return Objects.equals(left, right);
    }

    private void compareDerivedCount(Connection source, Connection target, List<String> failures,
                                     Map<String, Long> counts, String label, String sourceSql, String targetSql) throws SQLException {
        long expected = scalar(source, sourceSql);
        long actual = scalar(target, targetSql);
        counts.put(label, actual);
        if (expected != actual) failures.add("Count mismatch for " + label + ": expected=" + expected + ", actual=" + actual);
    }

    private long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) throw new SQLException("Query returned no row: " + sql);
            return result.getLong(1);
        }
    }

    @FunctionalInterface
    private interface RowEquality {
        boolean equal(ResultSet source, ResultSet target) throws Exception;
    }
}
