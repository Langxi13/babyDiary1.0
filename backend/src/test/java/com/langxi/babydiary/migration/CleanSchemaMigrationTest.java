package com.langxi.babydiary.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class CleanSchemaMigrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("baby_diary_v3")
                    .withUsername("baby_diary_test")
                    .withPassword("baby_diary_test");

    @Test
    void cleanSchemaMigratesWithUnifiedTypesAndWithoutLegacyMediaTables() throws Exception {
        migrate();

        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            Set<String> tables = tables(statement);
            assertThat(tables)
                    .contains(
                            "account",
                            "diary_space",
                            "diary",
                            "media_asset",
                            "media_variant",
                            "album",
                            "anniversary",
                            "ai_report",
                            "background_job",
                            "sync_change",
                            "outbox_event");
            assertThat(tables)
                    .doesNotContain(
                            "user",
                            "diary_image",
                            "album_photo",
                            "favorite_photo",
                            "media_legacy_map",
                            "search_document");

            assertThat(columnType(statement, "account", "account_id")).isEqualTo("bigint");
            assertThat(columnType(statement, "account", "public_id")).isEqualTo("binary(16)");
            assertThat(columnType(statement, "diary", "content_html")).isEqualTo("mediumtext");
            assertThat(columnType(statement, "diary_draft", "payload")).isEqualTo("json");
            assertThat(columnType(statement, "sync_change", "visibility")).isEqualTo("varchar(16)");

            try (ResultSet result =
                    statement.executeQuery(
                            "SELECT DISTINCT COLLATION_NAME "
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

        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO account (account_id, public_id, username, password_hash) "
                            + "VALUES (1, UUID_TO_BIN(UUID()), 'owner', 'hash')");
            statement.execute(
                    "INSERT INTO diary_space (space_id, public_id, name, type, created_by, personal_owner_id, default_visibility) "
                            + "VALUES (10, UUID_TO_BIN(UUID()), 'Owner space', 'PERSONAL', 1, 1, 'PRIVATE')");
            statement.execute(
                    "INSERT INTO diary_space (space_id, public_id, name, type, created_by, default_visibility) "
                            + "VALUES (20, UUID_TO_BIN(UUID()), 'Shared space', 'SHARED', 1, 'SHARED')");
            statement.execute(
                    "INSERT INTO diary (diary_id, public_id, space_id, author_id, title, diary_date, content_html, content_text) "
                            + "VALUES (100, UUID_TO_BIN(UUID()), 10, 1, 'Diary', '2026-07-29', '<p>Body</p>', 'Body')");
            statement.execute(
                    "INSERT INTO media_asset (asset_id, public_id, space_id, owner_id, media_type, status) "
                            + "VALUES (200, UUID_TO_BIN(UUID()), 20, 1, 'IMAGE', 'READY')");

            assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "INSERT INTO diary_media (space_id, diary_id, asset_id, position) "
                                                    + "VALUES (10, 100, 200, 0)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("foreign key constraint fails");
        }
    }

    @Test
    void v3DoesNotReplayHistoricalOutboxEventsAsFreshNotifications() throws Exception {
        clean();
        flyway("2").migrate();
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO account (account_id,public_id,username,password_hash) "
                            + "VALUES (1,UUID_TO_BIN(UUID()),'owner','hash')");
            statement.execute(
                    "INSERT INTO diary_space (space_id,public_id,name,type,created_by,personal_owner_id,default_visibility) "
                            + "VALUES (10,UUID_TO_BIN(UUID()),'Owner space','PERSONAL',1,1,'PRIVATE')");
            statement.execute(
                    "INSERT INTO outbox_event (public_id,space_id,aggregate_type,event_type,payload) "
                            + "VALUES (UUID_TO_BIN(UUID()),10,'DIARY','DIARY_CREATED',JSON_OBJECT('visibility','SHARED'))");
        }

        flyway(null).migrate();

        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT processed_at FROM outbox_event WHERE space_id=10")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getTimestamp(1)).isNotNull();
        }
    }

    @Test
    void v4BackfillsPublicRevisionIdsWithoutChangingRevisionData() throws Exception {
        clean();
        flyway("3").migrate();
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO account (account_id,public_id,username,password_hash) "
                            + "VALUES (1,UUID_TO_BIN(UUID()),'owner','hash')");
            statement.execute(
                    "INSERT INTO diary_space (space_id,public_id,name,type,created_by,personal_owner_id,default_visibility) "
                            + "VALUES (10,UUID_TO_BIN(UUID()),'Owner space','PERSONAL',1,1,'PRIVATE')");
            statement.execute(
                    "INSERT INTO diary (diary_id,public_id,space_id,author_id,title,diary_date,content_html,content_text) "
                            + "VALUES (100,UUID_TO_BIN(UUID()),10,1,'Diary','2026-07-30','<p>Body</p>','Body')");
            statement.execute(
                    "INSERT INTO diary (diary_id,public_id,space_id,author_id,title,diary_date,content_html,content_text,visibility) "
                            + "VALUES (101,UUID_TO_BIN(UUID()),10,1,'Shared','2026-07-30','<p>Body</p>','Body','SHARED')");
            statement.execute(
                    "INSERT INTO diary_revision (revision_id,diary_id,version,editor_id,snapshot) "
                            + "VALUES (1000,100,1,1,JSON_OBJECT('title','Diary'))");
            statement.execute(
                    "INSERT INTO sync_change (change_seq,space_id,entity_type,entity_public_id,operation,revision,actor_id) "
                            + "VALUES (2000,10,'DIARY',UUID_TO_BIN(UUID()),'DELETE',2,1)");
            statement.execute(
                    "INSERT INTO sync_change (change_seq,space_id,entity_type,entity_public_id,operation,revision,actor_id) "
                            + "SELECT 2001,10,'DIARY',public_id,'UPSERT',1,1 FROM diary WHERE diary_id=101");
        }

        flyway(null).migrate();

        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT revision_id,public_id,version,JSON_UNQUOTE(JSON_EXTRACT(snapshot,'$.title')) "
                                        + "FROM diary_revision WHERE revision_id=1000")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong(1)).isEqualTo(1000);
            assertThat(result.getBytes(2)).hasSize(16);
            assertThat(result.getInt(3)).isEqualTo(1);
            assertThat(result.getString(4)).isEqualTo("Diary");
        }
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT visibility,owner_id FROM sync_change WHERE change_seq=2000")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("PRIVATE");
            assertThat(result.getLong(2)).isEqualTo(1);
        }
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result =
                        statement.executeQuery(
                                "SELECT visibility,owner_id FROM sync_change WHERE change_seq=2001")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("SHARED");
            assertThat(result.getObject(2)).isNull();
        }
        try (Connection connection = connection();
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "INSERT INTO sync_change(space_id,entity_type,entity_public_id,operation,revision,visibility,owner_id,actor_id) "
                                                    + "VALUES(10,'DIARY',UUID_TO_BIN(UUID()),'UPSERT',1,'PRIVATE',NULL,1)"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(
                            () ->
                                    statement.execute(
                                            "INSERT INTO sync_change(space_id,entity_type,entity_public_id,operation,revision,visibility,owner_id,actor_id) "
                                                    + "VALUES(10,'DIARY',UUID_TO_BIN(UUID()),'UPSERT',1,'SHARED',1,1)"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void migrate() {
        clean();
        flyway(null).migrate();
    }

    private void clean() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
    }

    private Flyway flyway(String target) {
        var configuration =
                Flyway.configure()
                        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                        .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private Set<String> tables(Statement statement) throws SQLException {
        Set<String> tables = new java.util.HashSet<>();
        try (ResultSet result =
                statement.executeQuery(
                        "SELECT TABLE_NAME FROM information_schema.TABLES "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'")) {
            while (result.next()) tables.add(result.getString(1));
        }
        return tables;
    }

    private String columnType(Statement statement, String table, String column)
            throws SQLException {
        try (ResultSet result =
                statement.executeQuery(
                        "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '"
                                + table
                                + "' AND COLUMN_NAME = '"
                                + column
                                + "'")) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
