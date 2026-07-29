package com.langxi.babydiary.migration.v3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DiaryDataMigration extends V3JdbcMigration {
    private static final String NAMESPACE = "6f589db1-7ae8-5f24-b517-fbdbd84eaf12";

    void migrate(Connection source, Connection target) throws Exception {
        tags(source, target);
        diaries(source, target);
        diaryTags(source, target);
        drafts(source, target);
        comments(source, target);
        reactions(source, target);
        templates(source, target);
        revisions(source, target);
    }

    private void tags(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT tag_id,user_id,space_id,name,color,created_at FROM tag ORDER BY tag_id",
                "INSERT INTO tag(tag_id,public_id,space_id,name,color,created_by,created_at) VALUES(?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("tag_id");
                    insert.setLong(1, id);
                    insert.setBytes(2, tagUuid(id));
                    insert.setLong(3, row.getLong("space_id"));
                    insert.setString(4, row.getString("name"));
                    setString(insert, 5, row.getString("color"));
                    insert.setLong(6, row.getLong("user_id"));
                    setTime(insert, 7, requiredTime(row, "created_at"));
                });
    }

    private void diaries(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT diary_id,public_id,user_id,space_id,title,date,content,mood_key,content_format,visibility,locked,version,created_at,updated_at,deleted_at FROM diary ORDER BY diary_id",
                "INSERT INTO diary(diary_id,public_id,space_id,author_id,title,diary_date,content_html,content_text,mood_key,visibility,locked,version,created_at,updated_at,deleted_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("diary_id");
                    String content = row.getString("content");
                    String format = row.getString("content_format");
                    String html = V3MigrationSupport.contentHtml(content, format);
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.parseUuid(row.getString("public_id"), NAMESPACE, "diary:" + id));
                    insert.setLong(3, row.getLong("space_id"));
                    insert.setLong(4, row.getLong("user_id"));
                    insert.setString(5, row.getString("title"));
                    setDate(insert, 6, V3MigrationSupport.date(row, "date"));
                    insert.setString(7, html);
                    insert.setString(8, V3MigrationSupport.contentText(content, format, html));
                    setString(insert, 9, row.getString("mood_key"));
                    insert.setString(10, V3MigrationSupport.status(row.getString("visibility"), "PRIVATE"));
                    insert.setBoolean(11, row.getBoolean("locked"));
                    insert.setInt(12, row.getInt("version"));
                    setTime(insert, 13, requiredTime(row, "created_at"));
                    setTime(insert, 14, requiredTime(row, "updated_at"));
                    setTime(insert, 15, V3MigrationSupport.utc(row, "deleted_at"));
                });
    }

    private void diaryTags(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT d.space_id,dt.diary_id,dt.tag_id FROM diary_tag dt JOIN diary d ON d.diary_id=dt.diary_id ORDER BY dt.diary_id,dt.tag_id",
                "INSERT INTO diary_tag(space_id,diary_id,tag_id) VALUES(?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("space_id"));
                    insert.setLong(2, row.getLong("diary_id"));
                    insert.setLong(3, row.getLong("tag_id"));
                });
    }

    private void drafts(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT draft_id,user_id,space_id,draft_key,diary_id,title,date,content,content_format,mood_key,tag_ids,updated_at FROM diary_draft ORDER BY draft_id",
                "INSERT INTO diary_draft(draft_id,public_id,space_id,owner_id,diary_id,draft_key,payload,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("draft_id");
                    String content = row.getString("content");
                    String format = row.getString("content_format");
                    String html = V3MigrationSupport.contentHtml(content, format);
                    ObjectNode payload = V3MigrationSupport.object();
                    putNullable(payload, "title", row.getString("title"));
                    LocalDate date = V3MigrationSupport.date(row, "date");
                    if (date != null) payload.put("diaryDate", date.toString());
                    payload.put("contentHtml", html);
                    payload.put("contentText", V3MigrationSupport.contentText(content, format, html));
                    putNullable(payload, "mood", row.getString("mood_key"));
                    payload.set("tagIds", draftTagIds(row.getString("tag_ids")));
                    payload.set("mediaIds", V3MigrationSupport.array());
                    LocalDateTime updatedAt = requiredTime(row, "updated_at");

                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.uuid(NAMESPACE, "draft:" + id));
                    insert.setLong(3, row.getLong("space_id"));
                    insert.setLong(4, row.getLong("user_id"));
                    setLong(insert, 5, row, "diary_id");
                    insert.setString(6, row.getString("draft_key"));
                    insert.setString(7, V3MigrationSupport.stringify(payload));
                    setTime(insert, 8, updatedAt);
                    setTime(insert, 9, updatedAt);
                });
    }

    private void comments(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT comment_id,public_id,diary_id,user_id,content,created_at,updated_at,deleted_at FROM diary_comment ORDER BY comment_id",
                "INSERT INTO diary_comment(comment_id,public_id,diary_id,author_id,content,created_at,updated_at,deleted_at) VALUES(?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("comment_id");
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.parseUuid(row.getString("public_id"), NAMESPACE, "comment:" + id));
                    insert.setLong(3, row.getLong("diary_id"));
                    insert.setLong(4, row.getLong("user_id"));
                    insert.setString(5, row.getString("content"));
                    setTime(insert, 6, requiredTime(row, "created_at"));
                    setTime(insert, 7, requiredTime(row, "updated_at"));
                    setTime(insert, 8, V3MigrationSupport.utc(row, "deleted_at"));
                });
    }

    private void reactions(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT diary_id,user_id,emoji,created_at FROM diary_reaction ORDER BY diary_id,user_id,emoji",
                "INSERT INTO diary_reaction(diary_id,account_id,emoji,created_at) VALUES(?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("diary_id"));
                    insert.setLong(2, row.getLong("user_id"));
                    insert.setString(3, row.getString("emoji"));
                    setTime(insert, 4, requiredTime(row, "created_at"));
                });
    }

    private void templates(Connection source, Connection target) throws Exception {
        try (Statement statement = target.createStatement()) {
            statement.executeUpdate("DELETE FROM diary_template");
        }
        copy(source, target,
                "SELECT template_id,public_id,space_id,owner_user_id,template_key,name,description,icon,prompt_text,content_html,builtin,active,created_at,updated_at FROM diary_template ORDER BY template_id",
                "INSERT INTO diary_template(template_id,public_id,space_id,owner_id,template_key,name,description,icon,prompt_text,content_html,builtin,active,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("template_id");
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.parseUuid(row.getString("public_id"), NAMESPACE, "template:" + id));
                    setLong(insert, 3, row, "space_id");
                    setLong(insert, 4, row, "owner_user_id");
                    setString(insert, 5, row.getString("template_key"));
                    insert.setString(6, row.getString("name"));
                    setString(insert, 7, row.getString("description"));
                    setString(insert, 8, row.getString("icon"));
                    setString(insert, 9, row.getString("prompt_text"));
                    insert.setString(10, row.getString("content_html"));
                    insert.setBoolean(11, row.getBoolean("builtin"));
                    insert.setBoolean(12, row.getBoolean("active"));
                    setTime(insert, 13, requiredTime(row, "created_at"));
                    setTime(insert, 14, requiredTime(row, "updated_at"));
                });
    }

    private void revisions(Connection source, Connection target) throws Exception {
        Map<Long, List<String>> tagIds = diaryTagPublicIds(source);
        Map<Long, List<String>> mediaIds = diaryMediaPublicIds(source);
        Map<Long, List<Integer>> migratedVersions = new HashMap<>();

        copy(source, target,
                "SELECT r.revision_id,r.diary_id,r.version,r.editor_user_id,r.snapshot_json,r.created_at FROM diary_revision r ORDER BY r.revision_id",
                "INSERT INTO diary_revision(revision_id,diary_id,version,editor_id,snapshot,created_at) VALUES(?,?,?,?,?,?)",
                (row, insert) -> {
                    long diaryId = row.getLong("diary_id");
                    int version = row.getInt("version");
                    insert.setLong(1, row.getLong("revision_id"));
                    insert.setLong(2, diaryId);
                    insert.setInt(3, version);
                    insert.setLong(4, row.getLong("editor_user_id"));
                    insert.setString(5, migratedSnapshot(row.getString("snapshot_json"), tagIds.get(diaryId), mediaIds.get(diaryId)));
                    setTime(insert, 6, requiredTime(row, "created_at"));
                    migratedVersions.computeIfAbsent(diaryId, ignored -> new ArrayList<>()).add(version);
                });

        try (Statement query = source.createStatement(); ResultSet rows = query.executeQuery(
                "SELECT diary_id,user_id,title,date,content,content_format,mood_key,visibility,locked,version,updated_at FROM diary ORDER BY diary_id");
             PreparedStatement insert = target.prepareStatement(
                     "INSERT INTO diary_revision(diary_id,version,editor_id,snapshot,created_at) VALUES(?,?,?,?,?)")) {
            while (rows.next()) {
                long diaryId = rows.getLong("diary_id");
                int version = rows.getInt("version");
                if (migratedVersions.getOrDefault(diaryId, List.of()).contains(version)) continue;
                String content = rows.getString("content");
                String format = rows.getString("content_format");
                String html = V3MigrationSupport.contentHtml(content, format);
                ObjectNode snapshot = snapshot(
                        rows.getString("title"), V3MigrationSupport.date(rows, "date"), html,
                        V3MigrationSupport.contentText(content, format, html), rows.getString("mood_key"),
                        rows.getString("visibility"), rows.getBoolean("locked"), tagIds.get(diaryId), mediaIds.get(diaryId));
                insert.setLong(1, diaryId);
                insert.setInt(2, version);
                insert.setLong(3, rows.getLong("user_id"));
                insert.setString(4, V3MigrationSupport.stringify(snapshot));
                setTime(insert, 5, requiredTime(rows, "updated_at"));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private String migratedSnapshot(String legacyJson, List<String> tagIds, List<String> mediaIds) {
        try {
            JsonNode legacy = V3MigrationSupport.JSON.readTree(legacyJson);
            String content = legacy.path("content").asText("");
            String format = legacy.path("contentFormat").asText("plain");
            String html = V3MigrationSupport.contentHtml(content, format);
            LocalDate date = legacy.path("date").isTextual() && !legacy.path("date").asText().isBlank()
                    ? LocalDate.parse(legacy.path("date").asText()) : null;
            ObjectNode result = snapshot(legacy.path("title").asText(""), date, html,
                    V3MigrationSupport.contentText(content, format, html), textOrNull(legacy, "moodKey"),
                    legacy.path("visibility").asText("PRIVATE"), legacy.path("locked").asBoolean(false), tagIds, mediaIds);
            result.put("migratedFromLegacyRevision", true);
            return V3MigrationSupport.stringify(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid legacy diary revision JSON", exception);
        }
    }

    private ObjectNode snapshot(String title, LocalDate date, String html, String text, String mood,
                                String visibility, boolean locked, List<String> tags, List<String> media) {
        ObjectNode snapshot = V3MigrationSupport.object();
        snapshot.put("title", title == null ? "" : title);
        if (date != null) snapshot.put("diaryDate", date.toString());
        snapshot.put("contentHtml", html);
        snapshot.put("contentText", text);
        putNullable(snapshot, "mood", mood);
        snapshot.put("visibility", V3MigrationSupport.status(visibility, "PRIVATE"));
        snapshot.put("locked", locked);
        snapshot.set("tagIds", strings(tags));
        snapshot.set("mediaIds", strings(media));
        return snapshot;
    }

    private Map<Long, List<String>> diaryTagPublicIds(Connection source) throws Exception {
        Map<Long, List<String>> result = new HashMap<>();
        each(source, "SELECT diary_id,tag_id FROM diary_tag ORDER BY diary_id,tag_id", row -> {
            long diaryId = row.getLong("diary_id");
            result.computeIfAbsent(diaryId, ignored -> new ArrayList<>())
                    .add(V3MigrationSupport.uuidString(tagUuid(row.getLong("tag_id"))));
        });
        return result;
    }

    private Map<Long, List<String>> diaryMediaPublicIds(Connection source) throws Exception {
        Map<Long, List<String>> result = new HashMap<>();
        each(source, "SELECT dm.diary_id,a.public_id FROM diary_media dm JOIN media_asset a ON a.asset_id=dm.asset_id ORDER BY dm.diary_id,dm.sort,dm.asset_id", row ->
                result.computeIfAbsent(row.getLong("diary_id"), ignored -> new ArrayList<>()).add(row.getString("public_id")));
        return result;
    }

    private ArrayNode draftTagIds(String csv) {
        ArrayNode result = V3MigrationSupport.array();
        if (csv == null || csv.isBlank()) return result;
        for (String value : csv.split(",")) {
            try {
                long id = Long.parseLong(value.trim());
                if (id > 0) result.add(V3MigrationSupport.uuidString(tagUuid(id)));
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Invalid draft tag ID: " + value, exception);
            }
        }
        return result;
    }

    private ArrayNode strings(List<String> values) {
        ArrayNode result = V3MigrationSupport.array();
        if (values != null) values.forEach(result::add);
        return result;
    }

    private byte[] tagUuid(long id) {
        return V3MigrationSupport.uuid(NAMESPACE, "tag:" + id);
    }

    private void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field);
        else node.put(field, value);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
