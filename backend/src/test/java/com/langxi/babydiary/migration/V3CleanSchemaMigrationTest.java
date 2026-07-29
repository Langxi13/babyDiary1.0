package com.langxi.babydiary.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class V3CleanSchemaMigrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("baby_diary_v3")
            .withUsername("baby_diary_test")
            .withPassword("baby_diary_test");

    @Test
    void cleanSchemaMigratesWithUnifiedTypesAndWithoutLegacyMediaTables() throws Exception {
        migrate();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            Set<String> tables = tables(statement);
            assertThat(tables).contains(
                    "account", "diary_space", "diary", "media_asset", "media_variant",
                    "album", "anniversary", "ai_report", "background_job", "sync_change", "outbox_event");
            assertThat(tables).doesNotContain(
                    "user", "diary_image", "album_photo", "favorite_photo", "media_legacy_map", "search_document");

            assertThat(columnType(statement, "account", "account_id")).isEqualTo("bigint");
            assertThat(columnType(statement, "account", "public_id")).isEqualTo("binary(16)");
            assertThat(columnType(statement, "diary", "content_html")).isEqualTo("mediumtext");
            assertThat(columnType(statement, "diary_draft", "payload")).isEqualTo("json");

            try (ResultSet result = statement.executeQuery("SELECT DISTINCT COLLATION_NAME "
                    + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND COLLATION_NAME IS NOT NULL")) {
                while (result.next()) {
                    assertThat(result.getString(1)).doesNotContain("general_ci", "unicode_ci");
                }
            }
        }
    }

    @Test
    void compositeForeignKeysRejectCrossSpaceMediaRelations() throws Exception {
        migrate();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO account (account_id, public_id, username, password_hash) "
                    + "VALUES (1, UUID_TO_BIN(UUID()), 'owner', 'hash')");
            statement.execute("INSERT INTO diary_space (space_id, public_id, name, type, created_by, personal_owner_id, default_visibility) "
                    + "VALUES (10, UUID_TO_BIN(UUID()), 'Owner space', 'PERSONAL', 1, 1, 'PRIVATE')");
            statement.execute("INSERT INTO diary_space (space_id, public_id, name, type, created_by, default_visibility) "
                    + "VALUES (20, UUID_TO_BIN(UUID()), 'Shared space', 'SHARED', 1, 'SHARED')");
            statement.execute("INSERT INTO diary (diary_id, public_id, space_id, author_id, title, diary_date, content_html, content_text) "
                    + "VALUES (100, UUID_TO_BIN(UUID()), 10, 1, 'Diary', '2026-07-29', '<p>Body</p>', 'Body')");
            statement.execute("INSERT INTO media_asset (asset_id, public_id, space_id, owner_id, media_type, status) "
                    + "VALUES (200, UUID_TO_BIN(UUID()), 20, 1, 'IMAGE', 'READY')");

            assertThatThrownBy(() -> statement.execute("INSERT INTO diary_media (space_id, diary_id, asset_id, position) "
                    + "VALUES (10, 100, 200, 0)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("foreign key constraint fails");
        }
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/v3/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/v3/migration")
                .load()
                .migrate();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private Set<String> tables(Statement statement) throws SQLException {
        Set<String> tables = new java.util.HashSet<>();
        try (ResultSet result = statement.executeQuery("SELECT TABLE_NAME FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'")) {
            while (result.next()) tables.add(result.getString(1));
        }
        return tables;
    }

    private String columnType(Statement statement, String table, String column) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + table + "' AND COLUMN_NAME = '" + column + "'")) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
