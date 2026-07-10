package com.langxi.babydiary.service;

import com.langxi.babydiary.mapper.DiaryImageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryArchiveServiceTest {

    @Mock
    private DiaryImageMapper diaryImageMapper;

    @Spy
    private ImageStorageService imageStorageService = new ImageStorageService();

    @InjectMocks
    private DiaryArchiveService diaryArchiveService;

    @TempDir
    private Path uploadDir;

    @BeforeEach
    void setUpImageStorage() {
        ReflectionTestUtils.setField(imageStorageService, "uploadDir", uploadDir.toString());
    }

    @Test
    void exportImagesFindsFilesWhenUploadDirHasNoTrailingSlash() throws Exception {
        Files.write(uploadDir.resolve("export.jpg"), new byte[]{1, 2, 3});
        when(diaryImageMapper.findImagePathsByUserIdAndDateRange(3, "2026-06-01", "2026-06-08"))
                .thenReturn(Collections.singletonList("export.jpg"));

        FileSystemResource zip = diaryArchiveService.exportImagesAsZip(3, "2026-06-01", "2026-06-08");
        Path zipPath = zip.getFile().toPath();

        try (ZipInputStream zipInput = new ZipInputStream(zip.getInputStream())) {
            ZipEntry entry = zipInput.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("export.jpg");
        }
        assertThat(Files.exists(zipPath)).isFalse();
    }
}
