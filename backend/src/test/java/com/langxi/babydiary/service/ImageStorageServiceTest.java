package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageStorageServiceTest {

    @TempDir
    private Path uploadDir;

    @Test
    void storesOpaqueUniqueNamesWithoutLeakingOriginalFilename() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        MockMultipartFile first = imageFile("family-trip.jpg");
        MockMultipartFile second = imageFile("family-trip.jpg");

        String firstPath = service.storeImage(first, "diary_3_", true);
        String secondPath = service.storeImage(second, "diary_3_", true);

        assertThat(firstPath).startsWith("diary_3_").endsWith(".jpg").doesNotContain("family-trip");
        assertThat(secondPath).isNotEqualTo(firstPath);
        assertThat(Files.exists(uploadDir.resolve(firstPath))).isTrue();
        assertThat(Files.exists(uploadDir.resolve("thumbs/480").resolve(firstPath))).isTrue();
    }

    @Test
    void rejectsFilesThatOnlyPretendToBeImages() {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        MockMultipartFile forged = new MockMultipartFile(
                "imageFiles",
                "fake.jpg",
                "image/jpeg",
                "<script>alert(1)</script>".getBytes()
        );

        assertThatThrownBy(() -> service.storeImage(forged, "diary_3_", true))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FILE_TYPE_NOT_ALLOWED.getCode()));
    }

    @Test
    void rejectsOversizedLegacyImagesBeforeReadingThemIntoMemory() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getContentType()).thenReturn("image/jpeg");
        when(oversized.getSize()).thenReturn(10L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.storeImage(oversized, "diary_3_", true))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED.getCode()));

        verify(oversized, never()).getBytes();
    }

    @Test
    void removesNewFilesWhenTransactionRollsBack() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        TransactionSynchronizationManager.initSynchronization();
        try {
            String imagePath = service.storeImage(imageFile("rollback.jpg"), "diary_3_", true);
            assertThat(Files.exists(uploadDir.resolve(imagePath))).isTrue();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            assertThat(Files.exists(uploadDir.resolve(imagePath))).isFalse();
            assertThat(Files.exists(uploadDir.resolve("thumbs/480").resolve(imagePath))).isFalse();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesOldFilesOnlyAfterTransactionCommit() throws Exception {
        ImageStorageService service = new ImageStorageService(uploadDir.toString());
        String imagePath = service.storeImage(imageFile("old.jpg"), "diary_3_", true);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteAfterCommit(imagePath);
            assertThat(Files.exists(uploadDir.resolve(imagePath))).isTrue();

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            assertThat(Files.exists(uploadDir.resolve(imagePath))).isFalse();
            assertThat(Files.exists(uploadDir.resolve("thumbs/480").resolve(imagePath))).isFalse();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private MockMultipartFile imageFile(String filename) throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, Color.PINK.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return new MockMultipartFile("imageFiles", filename, "image/jpeg", output.toByteArray());
    }
}
