package com.langxi.babydiary.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoMapperXmlTest {

    @Test
    void photoListAndCountReuseFiltersAndPageAtTheDatabase() throws Exception {
        String xml = new String(
                Files.readAllBytes(Paths.get("src/main/resources/mapper/PhotoMapper.xml")),
                StandardCharsets.UTF_8
        );

        assertThat(xml).contains("<sql id=\"photoSourceAndFilters\"");
        assertThat(xml).contains("<select id=\"findPhotoPage\"");
        assertThat(xml).contains("LIMIT #{limit} OFFSET #{offset}");
        assertThat(xml).contains("<include refid=\"photoSourceAndFilters\"/>");
    }
}
