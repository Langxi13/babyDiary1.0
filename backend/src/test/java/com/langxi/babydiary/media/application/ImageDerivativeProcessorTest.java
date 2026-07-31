package com.langxi.babydiary.media.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageDerivativeProcessorTest {
    private static final List<String> TOOLS =
            List.of("vipsthumbnail", "vipsheader", "cwebp", "ffmpeg", "ffprobe");

    @TempDir Path directory;

    @BeforeAll
    static void requireTools() {
        for (String tool : TOOLS) {
            Assumptions.assumeTrue(commandExists(tool), () -> tool + " is not installed");
        }
    }

    @Test
    void createsBoundedAdaptivePhotoRepresentations() throws Exception {
        Path source = directory.resolve("photo.jpg");
        writePhoto(source, 1600, 1000);

        ImageDerivativeProcessor.Result result =
                processor().process(source, directory, "image/jpeg", Files.size(source));

        ImageDerivativeProcessor.Generated compact = variant(result, "THUMBNAIL");
        ImageDerivativeProcessor.Generated screen = variant(result, "PREVIEW");
        assertThat(compact.profile()).isEqualTo("compact");
        assertThat(compact.width()).isLessThanOrEqualTo(800);
        assertThat(compact.height()).isLessThanOrEqualTo(800);
        assertThat(compact.qualityScore()).isGreaterThanOrEqualTo(0.985);
        assertThat(screen.profile()).isEqualTo("screen");
        assertThat(screen.width()).isEqualTo(1600);
        assertThat(screen.height()).isEqualTo(1000);
        assertThat(screen.qualityScore()).isGreaterThanOrEqualTo(0.990);
        assertThat(Files.size(compact.path())).isLessThan(Files.size(source) * 9 / 10);
        assertThat(Files.size(screen.path())).isLessThan(Files.size(source) * 9 / 10);
    }

    @Test
    void keepsTransparencyLosslessAndNeverUpscales() throws Exception {
        Path source = directory.resolve("graphic.png");
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0, 0, 0, 0));
        graphics.fillRect(0, 0, 120, 80);
        graphics.setColor(new Color(190, 66, 83, 180));
        graphics.fillOval(10, 8, 96, 64);
        graphics.dispose();
        assertThat(ImageIO.write(image, "png", source.toFile())).isTrue();

        ImageDerivativeProcessor.Result result =
                processor().process(source, directory, "image/png", Long.MAX_VALUE / 100);

        assertThat(result.variants()).hasSize(1);
        assertThat(result.variants())
                .allSatisfy(
                        value -> {
                            assertThat(value.width()).isEqualTo(120);
                            assertThat(value.height()).isEqualTo(80);
                            assertThat(value.qualityScore()).isEqualTo(1.0);
                            assertThat(header(value.path(), "bands")).isEqualTo("4");
                        });
    }

    @Test
    void omitsRepresentationsThatDoNotSaveEnoughBytes() throws Exception {
        Path source = directory.resolve("small.jpg");
        writePhoto(source, 240, 160);

        ImageDerivativeProcessor.Result result =
                processor().process(source, directory, "image/jpeg", 1);

        assertThat(result.variants()).isEmpty();
    }

    @Test
    void animatedGifGetsOnlyAStaticCompactRepresentation() throws Exception {
        Path source = directory.resolve("animated.gif");
        run(
                List.of(
                        "ffmpeg",
                        "-nostdin",
                        "-v",
                        "error",
                        "-threads",
                        "1",
                        "-f",
                        "lavfi",
                        "-i",
                        "testsrc=size=160x100:rate=2",
                        "-t",
                        "1",
                        source.toString()));

        ImageDerivativeProcessor.Result result =
                processor().process(source, directory, "image/gif", Long.MAX_VALUE / 100);

        assertThat(result.animated()).isTrue();
        assertThat(result.variants())
                .extracting(ImageDerivativeProcessor.Generated::type)
                .containsExactly("THUMBNAIL");
    }

    private ImageDerivativeProcessor processor() {
        return new ImageDerivativeProcessor(
                "vipsthumbnail", "vipsheader", "cwebp", "ffmpeg", "ffprobe");
    }

    private ImageDerivativeProcessor.Generated variant(
            ImageDerivativeProcessor.Result result, String type) {
        return result.variants().stream()
                .filter(value -> type.equals(value.type()))
                .findFirst()
                .orElseThrow();
    }

    private void writePhoto(Path path, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int noise = ((x / 17) * 7 + (y / 13) * 11) & 0x1f;
                image.setRGB(
                        x,
                        y,
                        new Color(
                                        (x * 255 / width + noise) & 0xff,
                                        (y * 255 / height + noise) & 0xff,
                                        ((x + y) * 127 / (width + height) + noise) & 0xff)
                                .getRGB());
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(path.toFile())) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(1.0f);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    private String header(Path path, String field) throws Exception {
        return run(List.of("vipsheader", "-f", field, path.toString())).trim();
    }

    private static boolean commandExists(String command) {
        try {
            Process process = new ProcessBuilder("sh", "-c", "command -v " + command).start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String run(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(new String(output)).isZero();
        return new String(output);
    }
}
