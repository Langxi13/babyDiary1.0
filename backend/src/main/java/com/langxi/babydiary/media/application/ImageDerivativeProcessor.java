package com.langxi.babydiary.media.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ImageDerivativeProcessor {
    private static final long MINIMUM_SAVING_PERCENT = 10;

    private final String vipsthumbnail;
    private final String vipsheader;
    private final String cwebp;
    private final String ffmpeg;
    private final String ffprobe;

    public ImageDerivativeProcessor(
            @Value("${app.media.vipsthumbnail:vipsthumbnail}") String vipsthumbnail,
            @Value("${app.media.vipsheader:vipsheader}") String vipsheader,
            @Value("${app.media.cwebp:cwebp}") String cwebp,
            @Value("${app.media.ffmpeg:ffmpeg}") String ffmpeg,
            @Value("${app.media.ffprobe:ffprobe}") String ffprobe) {
        this.vipsthumbnail = vipsthumbnail;
        this.vipsheader = vipsheader;
        this.cwebp = cwebp;
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
    }

    public Result process(Path input, Path directory, String contentType, long originalBytes)
            throws Exception {
        boolean animated = isAnimated(input, contentType);
        boolean graphic = "image/png".equals(contentType) || bands(input) >= 4;
        Dimensions source = dimensions(input);
        List<Generated> values = new ArrayList<>();
        generate(input, directory, originalBytes, "THUMBNAIL", "compact", 800, graphic)
                .ifPresent(values::add);
        if (!animated && Math.max(source.width(), source.height()) > 800) {
            generate(input, directory, originalBytes, "PREVIEW", "screen", 2048, graphic)
                    .ifPresent(values::add);
        }
        return new Result(List.copyOf(values), animated);
    }

    private java.util.Optional<Generated> generate(
            Path input,
            Path directory,
            long originalBytes,
            String type,
            String profile,
            int maximumEdge,
            boolean lossless)
            throws Exception {
        Path reference = directory.resolve(profile + "-reference.png");
        thumbnail(input, reference, maximumEdge);
        Dimensions dimensions = dimensions(reference);
        Path output = directory.resolve(profile + ".webp");
        Double qualityScore;
        if (lossless) {
            encodeLossless(reference, output);
            qualityScore = 1.0;
        } else {
            int[] qualities =
                    "compact".equals(profile) ? new int[] {84, 88, 92} : new int[] {90, 92, 94};
            double minimum = "compact".equals(profile) ? 0.985 : 0.990;
            qualityScore = null;
            for (int quality : qualities) {
                encodePhoto(reference, output, quality);
                qualityScore = ssim(output, reference);
                if (qualityScore >= minimum) break;
            }
            if (qualityScore == null || qualityScore < minimum) return java.util.Optional.empty();
        }
        long bytes = Files.size(output);
        if (bytes <= 0 || !savesEnough(bytes, originalBytes)) {
            Files.deleteIfExists(output);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                new Generated(
                        type,
                        profile,
                        output,
                        "image/webp",
                        dimensions.width(),
                        dimensions.height(),
                        qualityScore));
    }

    private void thumbnail(Path input, Path output, int maximumEdge) throws Exception {
        Dimensions source = dimensions(input);
        int targetEdge = Math.min(maximumEdge, Math.max(source.width(), source.height()));
        run(
                List.of(
                        vipsthumbnail,
                        input.toString(),
                        "--size",
                        targetEdge + "x" + targetEdge,
                        "--output",
                        output + "[strip]",
                        "--export-profile",
                        "srgb",
                        "--vips-concurrency=1",
                        "--vips-cache-max=0",
                        "--vips-cache-max-memory=16777216"),
                180);
    }

    private void encodePhoto(Path input, Path output, int quality) throws Exception {
        run(
                List.of(
                        cwebp,
                        "-quiet",
                        "-preset",
                        "photo",
                        "-q",
                        Integer.toString(quality),
                        "-m",
                        "6",
                        "-sharp_yuv",
                        "-metadata",
                        "none",
                        input.toString(),
                        "-o",
                        output.toString()),
                180);
    }

    private void encodeLossless(Path input, Path output) throws Exception {
        run(
                List.of(
                        cwebp,
                        "-quiet",
                        "-lossless",
                        "-m",
                        "6",
                        "-exact",
                        "-metadata",
                        "none",
                        input.toString(),
                        "-o",
                        output.toString()),
                240);
    }

    private double ssim(Path encoded, Path reference) throws Exception {
        String output =
                command(
                        List.of(
                                ffmpeg,
                                "-nostdin",
                                "-v",
                                "info",
                                "-threads",
                                "1",
                                "-i",
                                encoded.toString(),
                                "-i",
                                reference.toString(),
                                "-lavfi",
                                "ssim",
                                "-f",
                                "null",
                                "-"),
                        120);
        int marker = output.lastIndexOf("All:");
        if (marker < 0) throw new IOException("SSIM result is missing");
        String value = output.substring(marker + 4).trim().split("\\s+")[0];
        return Double.parseDouble(value);
    }

    private Dimensions dimensions(Path input) throws Exception {
        int width = Integer.parseInt(field(input, "width"));
        int height = Integer.parseInt(field(input, "height"));
        return new Dimensions(width, height);
    }

    private int bands(Path input) throws Exception {
        return Integer.parseInt(field(input, "bands"));
    }

    private String field(Path input, String name) throws Exception {
        return command(List.of(vipsheader, "-f", name, input.toString()), 30).trim();
    }

    private boolean isAnimated(Path input, String contentType) throws Exception {
        if ("image/gif".equals(contentType)) return true;
        if (!"image/webp".equals(contentType)) return false;
        String output =
                command(
                        List.of(
                                ffprobe,
                                "-nostdin",
                                "-v",
                                "error",
                                "-count_frames",
                                "-select_streams",
                                "v:0",
                                "-show_entries",
                                "stream=nb_read_frames",
                                "-of",
                                "default=noprint_wrappers=1:nokey=1",
                                input.toString()),
                        30);
        try {
            return Integer.parseInt(output.trim()) > 1;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean savesEnough(long derivedBytes, long originalBytes) {
        return originalBytes > 0
                && derivedBytes * 100 <= originalBytes * (100 - MINIMUM_SAVING_PERCENT);
    }

    private void run(List<String> values, int timeoutSeconds) throws Exception {
        command(values, timeoutSeconds);
    }

    private String command(List<String> values, int timeoutSeconds) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(values).redirectErrorStream(true);
        builder.environment().put("VIPS_CONCURRENCY", "1");
        builder.environment().put("MALLOC_ARENA_MAX", "2");
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicReference<IOException> readFailure = new AtomicReference<>();
        Thread reader =
                new Thread(
                        () -> {
                            try (var stream = process.getInputStream()) {
                                stream.transferTo(output);
                            } catch (IOException exception) {
                                readFailure.set(exception);
                            }
                        },
                        "media-command-output");
        reader.setDaemon(true);
        reader.start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            reader.join(10_000);
            throw new IOException("Image processing timed out");
        }
        reader.join(10_000);
        if (reader.isAlive()) throw new IOException("Image command output did not close");
        if (readFailure.get() != null) throw readFailure.get();
        String text = output.toString(StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IOException(text.substring(0, Math.min(text.length(), 1000)));
        }
        return text;
    }

    public record Result(List<Generated> variants, boolean animated) {}

    public record Generated(
            String type,
            String profile,
            Path path,
            String contentType,
            int width,
            int height,
            double qualityScore) {}

    private record Dimensions(int width, int height) {}
}
