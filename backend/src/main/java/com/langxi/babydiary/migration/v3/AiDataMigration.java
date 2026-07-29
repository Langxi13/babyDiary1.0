package com.langxi.babydiary.migration.v3;

import com.fasterxml.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class AiDataMigration extends V3JdbcMigration {
    private static final String NAMESPACE = "6f589db1-7ae8-5f24-b517-fbdbd84eaf12";

    void migrate(Connection source, Connection target) throws Exception {
        config(source, target);
        reports(source, target);
        proposals(source, target);
        schedules(source, target);
    }

    private void config(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT config_id,enabled,base_url,model,encrypted_api_key,timeout_seconds,updated_at FROM ai_config ORDER BY config_id",
                "INSERT INTO ai_config(config_id,enabled,base_url,model,encrypted_api_key,timeout_seconds,updated_at) VALUES(?,?,?,?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("config_id"));
                    insert.setBoolean(2, row.getBoolean("enabled"));
                    setString(insert, 3, row.getString("base_url"));
                    setString(insert, 4, row.getString("model"));
                    setString(insert, 5, row.getString("encrypted_api_key"));
                    insert.setInt(6, row.getInt("timeout_seconds"));
                    setTime(insert, 7, requiredTime(row, "updated_at"));
                });
    }

    private void reports(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT report_id,user_id,space_id,type,period_start,period_end,title,content_markdown,diary_count,model,created_at FROM ai_report ORDER BY report_id",
                "INSERT INTO ai_report(report_id,public_id,space_id,created_by,period_type,period_start,period_end,title,content_markdown,diary_count,model,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("report_id");
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.uuid(NAMESPACE, "ai-report:" + id));
                    insert.setLong(3, row.getLong("space_id"));
                    insert.setLong(4, row.getLong("user_id"));
                    insert.setString(5, reportType(row.getString("type")));
                    setDate(insert, 6, V3MigrationSupport.date(row, "period_start"));
                    setDate(insert, 7, V3MigrationSupport.date(row, "period_end"));
                    insert.setString(8, row.getString("title"));
                    insert.setString(9, row.getString("content_markdown"));
                    insert.setInt(10, row.getInt("diary_count"));
                    setString(insert, 11, row.getString("model"));
                    setTime(insert, 12, requiredTime(row, "created_at"));
                });

        try (Statement query = source.createStatement(); ResultSet reports = query.executeQuery(
                "SELECT report_id,space_id,period_start,period_end FROM ai_report ORDER BY report_id");
             PreparedStatement diaries = source.prepareStatement(
                     "SELECT diary_id FROM diary WHERE space_id=? AND date BETWEEN ? AND ? AND deleted_at IS NULL ORDER BY date,diary_id");
             PreparedStatement insert = target.prepareStatement(
                     "INSERT INTO ai_report_diary(space_id,report_id,diary_id) VALUES(?,?,?)")) {
            while (reports.next()) {
                long spaceId = reports.getLong("space_id");
                diaries.setLong(1, spaceId);
                diaries.setDate(2, reports.getDate("period_start"));
                diaries.setDate(3, reports.getDate("period_end"));
                try (ResultSet diaryRows = diaries.executeQuery()) {
                    while (diaryRows.next()) {
                        insert.setLong(1, spaceId);
                        insert.setLong(2, reports.getLong("report_id"));
                        insert.setLong(3, diaryRows.getLong("diary_id"));
                        insert.addBatch();
                    }
                }
            }
            insert.executeBatch();
        }
    }

    private void proposals(Connection source, Connection target) throws Exception {
        Map<String, MediaRef> mediaByPublicId = media(source);
        copy(source, target,
                "SELECT proposal_id,user_id,space_id,status,start_date,end_date,prompt,model,created_at,updated_at FROM ai_album_proposal ORDER BY proposal_id",
                "INSERT INTO ai_album_proposal(proposal_id,public_id,space_id,created_by,status,start_date,end_date,prompt,model,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    long id = row.getLong("proposal_id");
                    insert.setLong(1, id);
                    insert.setBytes(2, V3MigrationSupport.uuid(NAMESPACE, "ai-album-proposal:" + id));
                    insert.setLong(3, row.getLong("space_id"));
                    insert.setLong(4, row.getLong("user_id"));
                    insert.setString(5, proposalStatus(row.getString("status")));
                    setDate(insert, 6, V3MigrationSupport.date(row, "start_date"));
                    setDate(insert, 7, V3MigrationSupport.date(row, "end_date"));
                    setString(insert, 8, row.getString("prompt"));
                    setString(insert, 9, row.getString("model"));
                    setTime(insert, 10, requiredTime(row, "created_at"));
                    setTime(insert, 11, requiredTime(row, "updated_at"));
                });

        try (Statement query = source.createStatement(); ResultSet proposals = query.executeQuery(
                "SELECT proposal_id,space_id,start_date,end_date,content_json FROM ai_album_proposal ORDER BY proposal_id");
             PreparedStatement candidate = target.prepareStatement(
                     "INSERT INTO ai_album_candidate(space_id,proposal_id,mode,target_album_id,title,description,start_date,end_date,discarded,position) VALUES(?,?,?,?,?,?,?,?,?,?)",
                     Statement.RETURN_GENERATED_KEYS);
             PreparedStatement candidateDiary = target.prepareStatement(
                     "INSERT INTO ai_album_candidate_diary(space_id,candidate_id,diary_id,position) VALUES(?,?,?,?)");
             PreparedStatement candidateMedia = target.prepareStatement(
                     "INSERT INTO ai_album_candidate_media(space_id,candidate_id,asset_id,position) VALUES(?,?,?,?)")) {
            while (proposals.next()) {
                long spaceId = proposals.getLong("space_id");
                JsonNode root = V3MigrationSupport.JSON.readTree(proposals.getString("content_json"));
                JsonNode albums = root.path("albums");
                if (!albums.isArray()) throw new IllegalStateException("AI proposal albums must be an array: " + proposals.getLong("proposal_id"));
                int position = 0;
                for (JsonNode album : albums) {
                    String mode = "MERGE".equalsIgnoreCase(album.path("mode").asText()) ? "MERGE" : "NEW";
                    candidate.setLong(1, spaceId);
                    candidate.setLong(2, proposals.getLong("proposal_id"));
                    candidate.setString(3, mode);
                    if ("MERGE".equals(mode) && album.path("targetAlbumId").canConvertToLong()) {
                        candidate.setLong(4, album.path("targetAlbumId").asLong());
                    } else candidate.setNull(4, Types.BIGINT);
                    candidate.setString(5, album.path("title").asText("AI 整理相册"));
                    setString(candidate, 6, textOrNull(album, "description"));
                    setDate(candidate, 7, V3MigrationSupport.date(proposals, "start_date"));
                    setDate(candidate, 8, V3MigrationSupport.date(proposals, "end_date"));
                    candidate.setBoolean(9, album.path("discarded").asBoolean(false));
                    candidate.setInt(10, position++);
                    candidate.executeUpdate();
                    long candidateId;
                    try (ResultSet keys = candidate.getGeneratedKeys()) {
                        if (!keys.next()) throw new IllegalStateException("AI album candidate insert returned no ID");
                        candidateId = keys.getLong(1);
                    }
                    addCandidateDiaries(candidateDiary, album.path("diaryIds"), spaceId, candidateId);
                    addCandidateMedia(candidateMedia, album.path("assetIds"), mediaByPublicId, spaceId, candidateId);
                }
            }
            candidateDiary.executeBatch();
            candidateMedia.executeBatch();
        }
    }

    private void schedules(Connection source, Connection target) throws Exception {
        copy(source, target,
                "SELECT space_id,weekly_enabled,monthly_enabled,annual_enabled,next_run_at,last_run_at,updated_by,updated_at FROM space_ai_schedule ORDER BY space_id",
                "INSERT INTO space_ai_schedule(space_id,weekly_enabled,monthly_enabled,annual_enabled,next_run_at,last_run_at,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?)",
                (row, insert) -> {
                    insert.setLong(1, row.getLong("space_id"));
                    insert.setBoolean(2, row.getBoolean("weekly_enabled"));
                    insert.setBoolean(3, row.getBoolean("monthly_enabled"));
                    insert.setBoolean(4, row.getBoolean("annual_enabled"));
                    setTime(insert, 5, V3MigrationSupport.utc(row, "next_run_at"));
                    setTime(insert, 6, V3MigrationSupport.utc(row, "last_run_at"));
                    insert.setLong(7, row.getLong("updated_by"));
                    setTime(insert, 8, requiredTime(row, "updated_at"));
                });
    }

    private Map<String, MediaRef> media(Connection source) throws Exception {
        Map<String, MediaRef> result = new HashMap<>();
        each(source, "SELECT public_id,asset_id,space_id FROM media_asset", row ->
                result.put(row.getString("public_id"), new MediaRef(row.getLong("asset_id"), row.getLong("space_id"))));
        return result;
    }

    private void addCandidateDiaries(PreparedStatement insert, JsonNode values, long spaceId, long candidateId) throws Exception {
        if (!values.isArray()) return;
        int position = 0;
        for (JsonNode value : values) {
            if (!value.canConvertToLong()) continue;
            insert.setLong(1, spaceId);
            insert.setLong(2, candidateId);
            insert.setLong(3, value.asLong());
            insert.setInt(4, position++);
            insert.addBatch();
        }
    }

    private void addCandidateMedia(PreparedStatement insert, JsonNode values, Map<String, MediaRef> media,
                                   long spaceId, long candidateId) throws Exception {
        if (!values.isArray()) return;
        int position = 0;
        for (JsonNode value : values) {
            MediaRef ref = media.get(value.asText());
            if (ref == null) throw new IllegalStateException("AI proposal references unknown media UUID");
            if (ref.spaceId() != spaceId) throw new IllegalStateException("AI proposal references media in another space");
            insert.setLong(1, spaceId);
            insert.setLong(2, candidateId);
            insert.setLong(3, ref.assetId());
            insert.setInt(4, position++);
            insert.addBatch();
        }
    }

    private String reportType(String value) {
        String type = V3MigrationSupport.status(value, "CUSTOM");
        return List.of("WEEKLY", "MONTHLY", "ANNUAL").contains(type) ? type : "CUSTOM";
    }

    private String proposalStatus(String value) {
        String status = V3MigrationSupport.status(value, "PENDING");
        if ("REJECTED".equals(status)) return "DISMISSED";
        return List.of("PENDING", "CONFIRMED", "DISMISSED", "FAILED").contains(status) ? status : "FAILED";
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private record MediaRef(long assetId, long spaceId) {
    }
}
