package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.MediaAsset;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;

class MediaMapperXmlTest {

    @Test
    void mediaMapperConformsToTheMyBatisMapperDtd() {
        Path mapperPath = Path.of("src/main/resources/mapper/MediaMapper.xml");

        assertThatCode(() -> {
            Configuration configuration = new Configuration();
            configuration.getTypeAliasRegistry().registerAlias("MediaAsset", MediaAsset.class);
            try (InputStream input = Files.newInputStream(mapperPath)) {
                new XMLMapperBuilder(
                        input,
                        configuration,
                        mapperPath.toString(),
                        configuration.getSqlFragments()
                ).parse();
            }
        }).doesNotThrowAnyException();
    }
}
