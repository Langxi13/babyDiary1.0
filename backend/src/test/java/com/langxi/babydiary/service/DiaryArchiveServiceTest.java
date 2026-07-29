package com.langxi.babydiary.service;

import com.langxi.babydiary.entity.MediaAsset;
import com.langxi.babydiary.mapper.MediaMapper;
import com.langxi.babydiary.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryArchiveServiceTest {
    @Mock private MediaMapper mediaMapper;
    @Mock private MediaService mediaService;
    @InjectMocks private DiaryArchiveService service;

    @Test
    void exportStreamsOriginalObjectsAndPreservesFilename() throws Exception {
        MediaAsset asset = new MediaAsset();
        asset.setPublicId("11111111-1111-1111-1111-111111111111");
        asset.setOriginalFilename("照片.jpg");
        asset.setStorageKey("spaces/one/photo.jpg");
        when(mediaMapper.findByUserAndDateRange(3, "2026-06-01", "2026-06-08")).thenReturn(List.of(asset));
        when(mediaService.openOriginalForExport(asset)).thenReturn(
                new StoredObject(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3, "image/jpeg"));

        var zip = service.exportImagesAsZip(3, "2026-06-01", "2026-06-08");
        var zipPath = zip.getFile().toPath();
        try (ZipInputStream input = new ZipInputStream(zip.getInputStream())) {
            assertThat(input.getNextEntry().getName()).isEqualTo("照片.jpg");
        }
        assertThat(Files.exists(zipPath)).isFalse();
    }
}
