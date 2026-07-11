package com.langxi.babydiary.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class AlbumPathCollationMigrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("legacy_schema")
            .withUsername("baby_diary_test")
            .withPassword("baby_diary_test");

    @Test
    void freshSchemaEndsWithCompatibleBinaryPathCollations() throws Exception {
        resetSchema();

        Flyway.configure()
                .dataSource(jdbcUrl("legacy_schema"), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertPathCollations("legacy_schema");
    }

    @Test
    void versionTwelveSchemaWithProductionCollationDriftMigratesAndComparesPaths() throws Exception {
        resetSchema();
        try (Connection connection = connection("legacy_schema"); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE diary_image ("
                    + "image_id INT PRIMARY KEY AUTO_INCREMENT,"
                    + "diary_id INT NOT NULL,"
                    + "image_path VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,"
                    + "sort INT NOT NULL) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci");
            statement.execute("CREATE TABLE album ("
                    + "album_id INT PRIMARY KEY AUTO_INCREMENT,"
                    + "cover_image_path VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL) "
                    + "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
            statement.execute("INSERT INTO diary_image (diary_id, image_path, sort) VALUES (1, 'cover-001.jpg', 0)");
            statement.execute("INSERT INTO album (cover_image_path) VALUES ('cover-001.jpg')");
        }

        Flyway.configure()
                .dataSource(jdbcUrl("legacy_schema"), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("12")
                .load()
                .migrate();

        assertPathCollations("legacy_schema");
        try (Connection connection = connection("legacy_schema");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM album a JOIN diary_image i "
                     + "ON i.image_path = a.cover_image_path")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    private void resetSchema() throws Exception {
        try (Connection connection = connection("legacy_schema"); Statement statement = connection.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet result = statement.executeQuery("SELECT TABLE_NAME FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA='legacy_schema'")) {
                while (result.next()) tables.add(result.getString(1));
            }
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (String table : tables) statement.execute("DROP TABLE `" + table + "`");
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    private void assertPathCollations(String database) throws Exception {
        try (Connection connection = connection(database); Statement statement = connection.createStatement()) {
            assertThat(collation(statement, database, "diary_image", "image_path")).isEqualTo("utf8mb4_bin");
            assertThat(collation(statement, database, "album", "cover_image_path")).isEqualTo("utf8mb4_bin");
        }
    }

    private String collation(Statement statement, String database, String table, String column) throws Exception {
        try (ResultSet result = statement.executeQuery("SELECT COLLATION_NAME FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA='" + database + "' AND TABLE_NAME='" + table + "' AND COLUMN_NAME='" + column + "'")) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private Connection connection(String database) throws Exception {
        return DriverManager.getConnection(jdbcUrl(database), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private String jdbcUrl(String database) {
        return MYSQL.getJdbcUrl().replace("/legacy_schema", "/" + database);
    }
}
