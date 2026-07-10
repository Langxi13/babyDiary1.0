package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AnniversaryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AnniversaryServiceTest {

    @Mock
    private AnniversaryMapper anniversaryMapper;

    @Spy
    private ImageStorageService imageStorageService = new ImageStorageService();

    @InjectMocks
    private AnniversaryService anniversaryService;

    @TempDir
    private Path uploadDir;

    @BeforeEach
    void setUpImageStorage() {
        ReflectionTestUtils.setField(imageStorageService, "uploadDir", uploadDir.toString());
    }

    @Test
    void uploadCoverStoresImageAndCreatesThumbnail() throws Exception {
        MockMultipartFile cover = new MockMultipartFile(
                "coverFile",
                "cover.jpg",
                "image/jpeg",
                imageBytes(1200, 900, "jpg"));

        String coverImagePath = anniversaryService.uploadCover(3, cover);

        assertThat(coverImagePath)
                .startsWith("anniversary_3_")
                .endsWith(".jpg")
                .doesNotContain("cover.jpg");
        assertThat(Files.exists(uploadDir.resolve(coverImagePath))).isTrue();
        assertThat(Files.exists(uploadDir.resolve("thumbs/480").resolve(coverImagePath))).isTrue();
    }

    @Test
    void uploadCoverRejectsNonImageFile() {
        MockMultipartFile cover = new MockMultipartFile(
                "coverFile",
                "cover.txt",
                "text/plain",
                new byte[]{1, 2, 3});

        assertThatThrownBy(() -> anniversaryService.uploadCover(3, cover))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED.getCode()));
    }

    private byte[] imageBytes(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, Color.PINK.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
