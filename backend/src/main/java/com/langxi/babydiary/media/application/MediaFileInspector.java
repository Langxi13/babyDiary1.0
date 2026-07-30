package com.langxi.babydiary.media.application;

import com.langxi.babydiary.platform.application.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class MediaFileInspector {
    static final long IMAGE_MAX_BYTES = 25L * 1024 * 1024;
    static final long AUDIO_VIDEO_MAX_BYTES = 256L * 1024 * 1024;
    static final long IMAGE_MAX_PIXELS = 80_000_000L;
    private static final Set<String> ALLOWED_DECLARED = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm", "video/quicktime",
            "audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav", "audio/x-wav");
    private static final Set<String> GENERIC_DECLARED = Set.of(
            "application/octet-stream", "binary/octet-stream");

    private final String ffprobe;

    public MediaFileInspector(@Value("${app.media.ffprobe:ffprobe}") String ffprobe) {
        this.ffprobe = ffprobe;
    }

    public Inspection inspect(Path path, String declaredType) throws IOException {
        long size = Files.size(path);
        if (size <= 0 || size > AUDIO_VIDEO_MAX_BYTES) {
            throw ApiException.badRequest("MEDIA_SIZE_INVALID", "媒体文件大小无效或超过上传限制");
        }
        String declared = normalizeDeclaredType(declaredType);
        if (!declared.isBlank() && !ALLOWED_DECLARED.contains(declared)) {
            throw ApiException.badRequest("MEDIA_TYPE_UNSUPPORTED", "暂不支持该媒体类型");
        }
        byte[] header;
        try (var input = Files.newInputStream(path)) {
            header = input.readNBytes(64);
        }
        String detected = detect(header);
        if (detected == null) throw ApiException.badRequest("MEDIA_CONTENT_INVALID", "媒体文件内容无效或格式不受支持");

        String mediaType = detected.startsWith("image/") ? "IMAGE"
                : detected.startsWith("audio/") ? "AUDIO" : "VIDEO";
        Integer width = null;
        Integer height = null;
        Long durationMillis = null;
        if ("IMAGE".equals(mediaType)) {
            if (size > IMAGE_MAX_BYTES) {
                throw ApiException.badRequest("MEDIA_SIZE_INVALID", "图片不能超过25MB");
            }
            int[] dimensions = imageDimensions(path);
            if (dimensions != null) {
                width = dimensions[0];
                height = dimensions[1];
            } else {
                Probe probe = probe(path);
                width = probe.width();
                height = probe.height();
            }
            requirePixelLimit(width, height);
        } else {
            Probe probe = probe(path);
            if (probe.video()) {
                mediaType = "VIDEO";
                width = probe.width();
                height = probe.height();
                if (detected.equals("audio/mp4")) detected = "video/mp4";
            } else if (probe.audio()) {
                mediaType = "AUDIO";
                if (detected.equals("video/mp4")) detected = "audio/mp4";
            } else {
                throw ApiException.badRequest("MEDIA_CONTENT_INVALID", "媒体文件不包含可用的音视频流");
            }
            durationMillis = probe.durationMillis();
        }
        if (!declared.isBlank() && !sameFormatFamily(declared, detected, mediaType)) {
            throw ApiException.badRequest("MEDIA_TYPE_MISMATCH", "文件内容与声明的媒体类型不一致");
        }
        return new Inspection(mediaType, detected, size, width, height, durationMillis);
    }

    private String detect(byte[] value) {
        if (starts(value, 0xff, 0xd8, 0xff)) return "image/jpeg";
        if (starts(value, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return "image/png";
        if (ascii(value, 0, "GIF87a") || ascii(value, 0, "GIF89a")) return "image/gif";
        if (ascii(value, 0, "RIFF") && ascii(value, 8, "WEBP")) return "image/webp";
        if (starts(value, 0x1a, 0x45, 0xdf, 0xa3)) return "video/webm";
        if (ascii(value, 0, "OggS")) return "audio/ogg";
        if (ascii(value, 0, "RIFF") && ascii(value, 8, "WAVE")) return "audio/wav";
        if (ascii(value, 0, "ID3") || (value.length >= 2 && (value[0] & 0xff) == 0xff
                && ((value[1] & 0xe0) == 0xe0))) return "audio/mpeg";
        if (value.length >= 12 && ascii(value, 4, "ftyp")) return "video/mp4";
        return null;
    }

    private Probe probe(Path path) throws IOException {
        Process process = new ProcessBuilder(ffprobe, "-v", "error", "-show_entries",
                "stream=codec_type,width,height:format=duration", "-of", "default=noprint_wrappers=1", path.toString())
                .redirectErrorStream(true).start();
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("ffprobe timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("ffprobe interrupted", exception);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw ApiException.badRequest("MEDIA_CONTENT_INVALID", "媒体文件无法解析");
        }
        boolean video = output.contains("codec_type=video");
        boolean audio = output.contains("codec_type=audio");
        Integer width = integerField(output, "width");
        Integer height = integerField(output, "height");
        String duration = field(output, "duration");
        Long durationMillis = null;
        if (duration != null && !"N/A".equals(duration)) {
            try { durationMillis = Math.max(0, Math.round(Double.parseDouble(duration) * 1000)); }
            catch (NumberFormatException ignored) { }
        }
        return new Probe(video, audio, width, height, durationMillis);
    }

    private int[] imageDimensions(Path path) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) return null;
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    private String field(String output, String name) {
        return Arrays.stream(output.split("\\R")).filter(line -> line.startsWith(name + "="))
                .map(line -> line.substring(name.length() + 1).trim()).filter(value -> !value.isBlank())
                .findFirst().orElse(null);
    }

    private Integer integerField(String output, String name) {
        try {
            String value = field(output, name);
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void requirePixelLimit(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0
                || (long) width * height > IMAGE_MAX_PIXELS) {
            throw ApiException.badRequest("MEDIA_DIMENSIONS_INVALID", "图片尺寸无效或超过8000万像素限制");
        }
    }

    private boolean sameFormatFamily(String declared, String detected, String mediaType) {
        if (declared.equals(detected)) return true;
        if ("AUDIO".equals(mediaType) && Set.of("audio/wav", "audio/x-wav").contains(declared)
                && "audio/wav".equals(detected)) return true;
        return Set.of("video/mp4", "video/quicktime", "audio/mp4").contains(declared)
                && Set.of("video/mp4", "audio/mp4").contains(detected);
    }

    private String normalizeDeclaredType(String value) {
        String declared = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (GENERIC_DECLARED.contains(declared)) return "";
        return "image/jpg".equals(declared) ? "image/jpeg" : declared;
    }

    private boolean starts(byte[] value, int... expected) {
        if (value.length < expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            if ((value[index] & 0xff) != expected[index]) return false;
        }
        return true;
    }

    private boolean ascii(byte[] value, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || value.length < offset + bytes.length) return false;
        for (int index = 0; index < bytes.length; index++) {
            if (value[offset + index] != bytes[index]) return false;
        }
        return true;
    }

    public record Inspection(String mediaType, String contentType, long sizeBytes,
                             Integer width, Integer height, Long durationMillis) {
    }

    private record Probe(boolean video, boolean audio, Integer width, Integer height, Long durationMillis) {
    }
}
