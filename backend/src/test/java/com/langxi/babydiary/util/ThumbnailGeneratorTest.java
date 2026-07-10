package com.langxi.babydiary.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThumbnailGeneratorTest {

    @TempDir
    private Path uploadDir;

    @Test
    void createThumbnailStoresResizedCopyUnderThumbDirectory() throws Exception {
        writeImage(uploadDir.resolve("large.jpg"), 1600, 1200, "jpg");

        Path thumbnail = ThumbnailGenerator.createThumbnail(uploadDir, "large.jpg");

        assertThat(thumbnail).isEqualTo(uploadDir.resolve("thumbs/480/large.jpg"));
        assertThat(Files.exists(thumbnail)).isTrue();
        BufferedImage thumb = ImageIO.read(thumbnail.toFile());
        assertThat(thumb.getWidth()).isEqualTo(480);
        assertThat(thumb.getHeight()).isEqualTo(360);
        assertThat(ThumbnailGenerator.thumbnailRelativePath("large.jpg")).isEqualTo("thumbs/480/large.jpg");
    }

    @Test
    void createThumbnailDoesNotUpscaleSmallImages() throws Exception {
        writeImage(uploadDir.resolve("small.jpg"), 120, 80, "jpg");

        Path thumbnail = ThumbnailGenerator.createThumbnail(uploadDir, "small.jpg");

        BufferedImage thumb = ImageIO.read(thumbnail.toFile());
        assertThat(thumb.getWidth()).isLessThanOrEqualTo(120);
        assertThat(thumb.getHeight()).isLessThanOrEqualTo(80);
    }

    @Test
    void deleteThumbnailRemovesGeneratedCopyOnly() throws Exception {
        writeImage(uploadDir.resolve("large.jpg"), 1600, 1200, "jpg");
        Path thumbnail = ThumbnailGenerator.createThumbnail(uploadDir, "large.jpg");

        ThumbnailGenerator.deleteThumbnail(uploadDir, "large.jpg");

        assertThat(Files.exists(thumbnail)).isFalse();
        assertThat(Files.exists(uploadDir.resolve("large.jpg"))).isTrue();
    }

    @Test
    void thumbnailPathCannotEscapeUploadDirectory() {
        assertThatThrownBy(() -> ThumbnailGenerator.createThumbnail(uploadDir, "../escape.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void writeImage(Path path, int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, Color.WHITE.getRGB());
            }
        }
        Files.createDirectories(path.getParent());
        ImageIO.write(image, format, path.toFile());
    }
}
