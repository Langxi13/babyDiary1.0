package com.langxi.babydiary.config;

import com.langxi.babydiary.mapper.InsightMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PrivacyDerivationBaselineTest {

    @Test
    void lockedDiariesStayOutOfSearchAndInsights() throws Exception {
        String searchMapper = Files.readString(
                Path.of("src/main/resources/mapper/SearchMapper.xml"), StandardCharsets.UTF_8);
        String collaborationMapper = Files.readString(
                Path.of("src/main/resources/mapper/CollaborationMapper.xml"), StandardCharsets.UTF_8);
        String diaryMapper = Files.readString(
                Path.of("src/main/resources/mapper/DiaryMapper.xml"), StandardCharsets.UTF_8);
        String photoMapper = Files.readString(
                Path.of("src/main/resources/mapper/PhotoMapper.xml"), StandardCharsets.UTF_8);
        String albumMapper = Files.readString(
                Path.of("src/main/resources/mapper/AlbumMapper.xml"), StandardCharsets.UTF_8);
        String draftMapper = Files.readString(
                Path.of("src/main/resources/mapper/DiaryDraftMapper.xml"), StandardCharsets.UTF_8);
        String imageMapper = Files.readString(
                Path.of("src/main/resources/mapper/DiaryImageMapper.xml"), StandardCharsets.UTF_8);
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V11__search_templates_media_sharing.sql"),
                StandardCharsets.UTF_8);

        assertThat(InsightMapper.VISIBLE).contains("d.locked=0");
        assertThat(searchMapper).contains("deleted_at IS NULL AND locked=0");
        assertThat(searchMapper).contains("d.deleted_at IS NULL AND d.locked=0");
        assertThat(collaborationMapper).contains("AND d.locked = 0");
        assertThat(diaryMapper)
                .contains("deleted_at IS NULL AND locked = 0")
                .contains("AND d.locked = 0");
        assertThat(photoMapper)
                .contains("AND d.deleted_at IS NULL")
                .contains("AND d.locked = 0");
        assertThat(albumMapper)
                .contains("d_explicit.locked = 0")
                .contains("d_cover.locked = 0")
                .contains("d_count.locked = 0");
        assertThat(draftMapper).contains("d.deleted_at IS NULL AND d.locked = 0");
        assertThat(imageMapper).contains("AND d.locked = 0");
        assertThat(migration).contains("`deleted_at` IS NULL AND `locked` = 0");
    }

    @Test
    void productionConfigKeepsPrivateMediaAndDatabaseTimeIsolated() throws Exception {
        String defaultConfig = Files.readString(
                Path.of("src/main/resources/application.yml"), StandardCharsets.UTF_8);
        String productionConfig = Files.readString(
                Path.of("../config/application-prod.yml"), StandardCharsets.UTF_8);

        assertThat(defaultConfig)
                .contains("connectionTimeZone=%2B08:00")
                .contains("forceConnectionTimeZoneToSession=true")
                .contains("local-root: ${DIARY_OBJECT_PATH:${user.dir}/data/objects/}");
        assertThat(productionConfig)
                .contains("local-root: ${DIARY_OBJECT_PATH:${user.dir}/data/objects/}")
                .contains("enabled: ${SPRINGDOC_ENABLED:false}")
                .doesNotContain("${DIARY_FILE_PATH}objects/");
    }
}
