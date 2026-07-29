package com.langxi.babydiary.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.service.LegacyMediaMigrationService;
import com.langxi.babydiary.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class UnifiedMediaMigrationTest {
    @TempDir Path legacyRoot;

    @Test
    void v15AddsTypedRelationsWithoutDroppingRollbackTables() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V15__unify_media_assets.sql"));

        assertThat(sql).contains(
                "CREATE TABLE `album_media`",
                "CREATE TABLE `favorite_media`",
                "CREATE TABLE `user_avatar`",
                "CREATE TABLE `media_legacy_map`",
                "ADD COLUMN `cover_asset_id`",
                "ADD COLUMN `checksum_sha256`");
        assertThat(sql.toUpperCase()).doesNotContain(
                "DROP TABLE `DIARY_IMAGE`",
                "DROP TABLE `ALBUM_PHOTO`",
                "DROP TABLE `FAVORITE_PHOTO`");
    }

    @Test
    void applyRequiresConfirmationInsideTheMigrationService() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectStorage storage = mock(ObjectStorage.class);
        LegacyMediaMigrationService service = new LegacyMediaMigrationService(
                jdbc, storage, new ObjectMapper(), legacyRoot.toString(), "");

        assertThatThrownBy(service::apply)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmation");
        verifyNoInteractions(jdbc, storage);
    }
}
