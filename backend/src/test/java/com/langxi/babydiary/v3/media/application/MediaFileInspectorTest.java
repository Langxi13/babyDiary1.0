package com.langxi.babydiary.v3.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.langxi.babydiary.v3.platform.application.V3Exception;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaFileInspectorTest {
  @TempDir Path directory;

  @Test
  void derivesImageTypeAndDimensionsFromFileContent() throws Exception {
    Path image = png();

    MediaFileInspector.Inspection result = inspector().inspect(image, "image/png");

    assertThat(result.mediaType()).isEqualTo("IMAGE");
    assertThat(result.contentType()).isEqualTo("image/png");
    assertThat(result.width()).isEqualTo(3);
    assertThat(result.height()).isEqualTo(2);
    assertThat(result.sizeBytes()).isEqualTo(Files.size(image));
  }

  @Test
  void rejectsDeclaredTypeMismatchAndFakeImageContent() throws Exception {
    assertThatThrownBy(() -> inspector().inspect(png(), "image/jpeg"))
        .isInstanceOfSatisfying(
            V3Exception.class,
            exception -> assertThat(exception.code()).isEqualTo("MEDIA_TYPE_MISMATCH"));

    Path fake = directory.resolve("fake.png");
    Files.writeString(fake, "not an image", StandardCharsets.UTF_8);
    assertThatThrownBy(() -> inspector().inspect(fake, "image/png"))
        .isInstanceOfSatisfying(
            V3Exception.class,
            exception -> assertThat(exception.code()).isEqualTo("MEDIA_CONTENT_INVALID"));
  }

  @Test
  void rejectsEmptyAndUnsupportedUploadsBeforeProcessing() throws Exception {
    Path empty = directory.resolve("empty.png");
    Files.createFile(empty);
    assertThatThrownBy(() -> inspector().inspect(empty, "image/png"))
        .isInstanceOfSatisfying(
            V3Exception.class,
            exception -> assertThat(exception.code()).isEqualTo("MEDIA_SIZE_INVALID"));

    assertThatThrownBy(() -> inspector().inspect(png(), "text/html"))
        .isInstanceOfSatisfying(
            V3Exception.class,
            exception -> assertThat(exception.code()).isEqualTo("MEDIA_TYPE_UNSUPPORTED"));
  }

  @Test
  void acceptsGenericClientMimeTypesAndNormalizesImageJpg() throws Exception {
    assertThat(inspector().inspect(png(), "application/octet-stream").mediaType()).isEqualTo("IMAGE");
    assertThat(inspector().inspect(image("jpg"), "image/jpg").contentType()).isEqualTo("image/jpeg");
  }

  private MediaFileInspector inspector() {
    return new MediaFileInspector("ffprobe-not-used-for-images");
  }

  private Path png() throws Exception {
    return image("png");
  }

  private Path image(String format) throws Exception {
    Path path = directory.resolve("image-" + System.nanoTime() + "." + format);
    BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
    assertThat(ImageIO.write(image, format, path.toFile())).isTrue();
    return path;
  }
}
