package com.langxi.babydiary.migration;

import com.langxi.babydiary.migration.v3.V3MigrationCli;
import com.langxi.babydiary.migration.v3.V3MigrationReport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class V3DataMigrationIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("source_v15")
            .withUsername("baby_diary_test")
            .withPassword("baby_diary_test");

    @TempDir
    Path objectRoot;

    @Test
    void migratesRepresentativeV15DataAndVerifiesSemantics() throws Exception {
        migrateLegacySchema();
        createTargetDatabase();
        seedSource();

        String sourceUrl = MYSQL.getJdbcUrl();
        String targetUrl = sourceUrl.replace("/source_v15", "/target_v3");
        V3MigrationReport report = V3MigrationCli.executeForTest(new String[]{
                "migrate",
                "--source-url=" + sourceUrl,
                "--source-user=" + MYSQL.getUsername(),
                "--source-password=" + MYSQL.getPassword(),
                "--target-url=" + targetUrl,
                "--target-user=" + MYSQL.getUsername(),
                "--target-password=" + MYSQL.getPassword(),
                "--object-root=" + objectRoot,
                "--confirm=MIGRATE_TO_V3"
        });
        assertThat(report.valid()).withFailMessage("%s", report.failures()).isTrue();

        try (Connection target = DriverManager.getConnection(targetUrl, MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = target.createStatement()) {
            assertThat(scalar(statement, "SELECT COUNT(*) FROM account")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM diary")).isEqualTo(2);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM diary_revision")).isEqualTo(2);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM media_variant")).isEqualTo(2);
            assertThat(string(statement, "SELECT profile FROM media_variant WHERE variant_type='ORIGINAL'"))
                    .isEqualTo("source");
            assertThat(string(statement, "SELECT profile FROM media_variant WHERE variant_type='THUMBNAIL'"))
                    .isEqualTo("default");
            assertThat(report.checks()).contains("media-variant-availability");
            assertThat(scalar(statement, "SELECT COUNT(*) FROM ai_album_candidate")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM ai_album_candidate_media")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM ai_album_candidate_diary")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM auth_session")).isZero();
            assertThat(string(statement, "SELECT content_html FROM diary WHERE diary_id=1"))
                    .isEqualTo("<p>first line<br>second &lt;line&gt;</p>");
            assertThat(string(statement, "SELECT content_text FROM diary WHERE diary_id=1"))
                    .isEqualTo("first line\nsecond <line>");
            assertThat(string(statement, "SELECT content_html FROM diary WHERE diary_id=2"))
                    .isEqualTo("<p><strong>HTML</strong> diary</p>");
            assertThat(string(statement, "SELECT content_text FROM diary WHERE diary_id=2"))
                    .isEqualTo("HTML diary");
            assertThat(scalar(statement, "SELECT used_bytes FROM space_storage_usage WHERE space_id=1"))
                    .isEqualTo(Files.size(objectRoot.resolve("spaces/demo/original.jpg"))
                            + Files.size(objectRoot.resolve("spaces/demo/thumb.jpg")));
            assertThat(string(statement, "SELECT DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s') FROM space_storage_usage WHERE space_id=1"))
                    .isEqualTo("2026-07-29 02:00:00");
        }
    }

    private void migrateLegacySchema() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private void createTargetDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS target_v3");
            statement.execute("CREATE DATABASE target_v3 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            statement.execute("GRANT ALL PRIVILEGES ON target_v3.* TO 'baby_diary_test'@'%'");
        }
    }

    private void seedSource() throws Exception {
        Path original = objectRoot.resolve("spaces/demo/original.jpg");
        Path thumbnail = objectRoot.resolve("spaces/demo/thumb.jpg");
        Files.createDirectories(original.getParent());
        Files.write(original, new byte[]{1, 2, 3, 4, 5});
        Files.write(thumbnail, new byte[]{6, 7, 8});
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(original)));

        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement sql = connection.createStatement()) {
            sql.execute("SET SESSION time_zone='+08:00'");
            sql.execute("INSERT INTO user(user_id,username,email,email_verified,password,created_at,token_version,system_role,timezone) "
                    + "VALUES(1,'owner','owner@example.com',1,'password-hash','2026-07-29 10:00:00',2,'ADMIN','Asia/Shanghai')");
            sql.execute("INSERT INTO diary_space(space_id,public_id,name,type,created_by,personal_owner_id,default_visibility,storage_quota_bytes,created_at,updated_at) "
                    + "VALUES(1,'11111111-1111-4111-8111-111111111111','Personal','PERSONAL',1,1,'PRIVATE',5368709120,'2026-07-29 10:00:00','2026-07-29 10:00:00')");
            sql.execute("INSERT INTO space_member(space_id,user_id,role,status,joined_at) VALUES(1,1,'OWNER','ACTIVE','2026-07-29 10:00:00')");
            sql.execute("INSERT INTO space_storage_usage(space_id,used_bytes,updated_at) VALUES(1,5,'2026-07-29 10:00:00')");
            sql.execute("INSERT INTO tag(tag_id,user_id,space_id,name,color,created_at) VALUES(1,1,1,'旅行','#58b368','2026-07-29 10:00:00')");
            sql.execute("INSERT INTO diary(diary_id,public_id,user_id,space_id,title,date,content,mood_key,content_format,visibility,locked,version,created_at,updated_at) "
                    + "VALUES(1,'22222222-2222-4222-8222-222222222222',1,1,'Plain','2026-07-28','first line\\nsecond <line>','happy','plain','PRIVATE',0,1,'2026-07-28 12:00:00','2026-07-28 12:00:00')");
            sql.execute("INSERT INTO diary(diary_id,public_id,user_id,space_id,title,date,content,content_format,visibility,locked,version,created_at,updated_at,deleted_at) "
                    + "VALUES(2,'33333333-3333-4333-8333-333333333333',1,1,'HTML','2026-07-29','<p><strong>HTML</strong> diary</p>','html','PRIVATE',0,1,'2026-07-29 12:00:00','2026-07-29 12:00:00','2026-07-29 13:00:00')");
            sql.execute("INSERT INTO diary_tag(diary_id,tag_id) VALUES(1,1)");
            sql.execute("INSERT INTO media_asset(asset_id,public_id,space_id,owner_user_id,media_type,original_filename,storage_provider,storage_key,thumbnail_key,content_type,size_bytes,checksum_sha256,access_scope,library_visible,width,height,status,created_at,updated_at) "
                    + "VALUES(1,'44444444-4444-4444-8444-444444444444',1,1,'IMAGE','original.jpg','LOCAL','spaces/demo/original.jpg','spaces/demo/thumb.jpg','image/jpeg',5,'" + checksum + "','LINKED',1,10,10,'READY','2026-07-28 12:00:00','2026-07-28 12:00:00')");
            sql.execute("INSERT INTO media_legacy_map(space_id,legacy_path,asset_id,checksum_sha256) VALUES(1,'original.jpg',1,'" + checksum + "')");
            sql.execute("INSERT INTO diary_media(diary_id,asset_id,sort,created_at) VALUES(1,1,0,'2026-07-28 12:00:00')");
            sql.execute("INSERT INTO album_group(group_id,user_id,space_id,name,type,sort,created_at,updated_at) VALUES(1,1,1,'AI 相册','AI',0,'2026-07-29 10:00:00','2026-07-29 10:00:00')");
            sql.execute("INSERT INTO album(album_id,group_id,user_id,space_id,name,description,type,cover_asset_id,sort,created_at,updated_at) VALUES(1,1,1,1,'Trip','A trip','AI',1,0,'2026-07-29 10:00:00','2026-07-29 10:00:00')");
            sql.execute("INSERT INTO album_media(album_id,asset_id,sort,created_at) VALUES(1,1,0,'2026-07-29 10:00:00')");
            sql.execute("INSERT INTO favorite_media(user_id,asset_id,created_at) VALUES(1,1,'2026-07-29 10:00:00')");
            sql.execute("INSERT INTO user_avatar(user_id,asset_id,updated_at) VALUES(1,1,'2026-07-29 10:00:00')");
            sql.execute("INSERT INTO anniversary(anniversary_id,user_id,space_id,title,date,description,cover_asset_id,sort,created_at,updated_at) VALUES(1,1,1,'First day','2026-07-01','Memory',1,0,'2026-07-29 10:00:00','2026-07-29 10:00:00')");
            sql.execute("INSERT INTO ai_report(report_id,user_id,space_id,scope,type,period,period_start,period_end,title,content_markdown,diary_count,model,created_at) VALUES(1,1,1,'PERSONAL','MONTHLY','2026-07','2026-07-01','2026-07-31','Monthly','# Report',1,'demo-model','2026-07-29 10:00:00')");
            sql.execute("INSERT INTO ai_album_proposal(proposal_id,user_id,space_id,status,start_date,end_date,prompt,content_json,model,created_at,updated_at) VALUES(1,1,1,'CONFIRMED','2026-07-01','2026-07-31',NULL,'{\\\"albums\\\":[{\\\"mode\\\":\\\"NEW\\\",\\\"title\\\":\\\"Trip\\\",\\\"description\\\":\\\"Demo\\\",\\\"diaryIds\\\":[1],\\\"assetIds\\\":[\\\"44444444-4444-4444-8444-444444444444\\\"],\\\"discarded\\\":false}]}','demo-model','2026-07-29 10:00:00','2026-07-29 10:00:00')");
        }
    }

    private long scalar(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private String string(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
