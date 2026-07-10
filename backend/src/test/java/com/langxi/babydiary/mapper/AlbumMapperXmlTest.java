package com.langxi.babydiary.mapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AlbumMapperXmlTest {

    @Test
    void albumQueriesUseExistingAlbumPhotosAsFallbackCover() throws Exception {
        String xml = new String(
                Files.readAllBytes(Paths.get("src/main/resources/mapper/AlbumMapper.xml")),
                StandardCharsets.UTF_8
        );

        assertThat(xml).contains("COALESCE(a.cover_image_path");
        assertThat(xml).contains("FROM album_photo ap_cover");
        assertThat(xml).contains("ORDER BY ap_cover.sort ASC, i.image_id ASC");
        assertThat(xml).contains("ORDER BY ap.sort ASC, ap.image_id ASC");
        assertThat(xml).contains("<select id=\"findAlbumPhotoPage\"");
        assertThat(xml).contains("<select id=\"countAlbumPhotos\"");
        assertThat(xml).contains("LIMIT #{limit} OFFSET #{offset}");
        assertThat(xml).doesNotContain("ORDER BY RAND()");
        assertThat(xml).doesNotContain("SELECT a.*,\n               COUNT(ap.image_id) AS photo_count");
    }
}
