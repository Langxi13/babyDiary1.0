package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.Album;
import com.langxi.babydiary.entity.AlbumGroup;
import com.langxi.babydiary.entity.Photo;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AlbumMapperXmlTest {
    @Test
    void albumQueriesUseTypedMediaRelationsAndValidMyBatisXml() throws Exception {
        Path path = Path.of("src/main/resources/mapper/AlbumMapper.xml");
        String xml = Files.readString(path);
        assertThat(xml).contains("FROM album_media", "cover_asset_id", "a_asset.deleted_at IS NULL");
        assertThat(xml).contains("nextAlbumPhotoSort", "#{sort}", "a.space_id=alb.space_id");
        assertThat(xml).doesNotContain("album_photo", "diary_image", "ORDER BY RAND()");

        assertThatCode(() -> {
            Configuration configuration = new Configuration();
            configuration.getTypeAliasRegistry().registerAlias("Album", Album.class);
            configuration.getTypeAliasRegistry().registerAlias("AlbumGroup", AlbumGroup.class);
            configuration.getTypeAliasRegistry().registerAlias("Photo", Photo.class);
            try (InputStream input = Files.newInputStream(path)) {
                new XMLMapperBuilder(input, configuration, path.toString(), configuration.getSqlFragments()).parse();
            }
        }).doesNotThrowAnyException();
    }
}
