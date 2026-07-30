package com.langxi.babydiary.media.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.BackgroundJobHandler;
import com.langxi.babydiary.storage.ObjectStorage;
import com.langxi.babydiary.storage.ObjectStorageRegistry;
import com.langxi.babydiary.storage.StoredObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MediaProcessingJobHandler implements BackgroundJobHandler {
    private final MediaRepository media;
    private final MediaVariantPolicy variants;
    private final ObjectStorageRegistry storages;
    private final ObjectMapper json;
    private final String ffmpeg;
    private final String ffprobe;
    private final long minimumFreeBytes;

    public MediaProcessingJobHandler(
            MediaRepository media,
            MediaVariantPolicy variants,
            ObjectStorageRegistry storages,
            ObjectMapper json,
            @Value("${app.media.ffmpeg:ffmpeg}") String ffmpeg,
            @Value("${app.media.ffprobe:ffprobe}") String ffprobe,
            @Value("${app.media.processing-min-free-bytes:3221225472}") long minimumFreeBytes) {
        this.media = media;
        this.variants = variants;
        this.storages = storages;
        this.json = json;
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.minimumFreeBytes = Math.max(0, minimumFreeBytes);
    }

    @Override
    public String type() {
        return "MEDIA_PROCESS";
    }

    @Override
    public JsonNode handle(JsonNode payload) throws Exception {
        UUID spaceId = uuid(payload, "spaceId");
        UUID assetId = uuid(payload, "assetId");
        MediaAsset asset =
                media.findInSpace(spaceId, assetId, false)
                        .orElseThrow(() -> ApiException.notFound("MEDIA_NOT_FOUND", "媒体不存在"));
        MediaAsset.Variant original =
                variants.select(asset.variants(), "ORIGINAL", null)
                        .orElseThrow(
                                () ->
                                        ApiException.notFound(
                                                "MEDIA_VARIANT_NOT_FOUND", "媒体原始文件不存在"));
        Path directory = Files.createTempDirectory("baby-diary-v3-process-");
        try {
            requireTemporarySpace(directory);
            Path input = directory.resolve("input" + extension(original.contentType()));
            try (StoredObject object =
                            storages.require(original.storageProvider())
                                    .get(original.storageKey());
                    InputStream stream = object.stream()) {
                Files.copy(stream, input);
            }
            switch (asset.mediaType()) {
                case "IMAGE" -> processImage(asset, input, directory);
                case "VIDEO" -> processVideo(asset, input, directory);
                case "AUDIO" -> processAudio(asset, input, directory);
                default ->
                        throw new IllegalArgumentException(
                                "Unsupported media type: " + asset.mediaType());
            }
            return json.valueToTree(Map.of("assetId", asset.id().toString(), "processed", true));
        } finally {
            deleteDirectory(directory);
        }
    }

    private void processImage(MediaAsset asset, Path input, Path directory) throws Exception {
        if (media.hasVariant(asset.internalId(), "THUMBNAIL", "default")) return;
        Path thumbnail = directory.resolve("thumbnail.jpg");
        BufferedImage image = ImageIO.read(input.toFile());
        Integer width = null;
        Integer height = null;
        if (image != null) {
            width = image.getWidth();
            height = image.getHeight();
            Thumbnails.of(image)
                    .size(1280, 1280)
                    .outputFormat("jpg")
                    .outputQuality(0.84)
                    .toFile(thumbnail.toFile());
        } else {
            run(
                    List.of(
                            ffmpeg,
                            "-v",
                            "error",
                            "-y",
                            "-threads",
                            "1",
                            "-i",
                            input.toString(),
                            "-vf",
                            "scale='min(1280,iw)':-2",
                            "-frames:v",
                            "1",
                            thumbnail.toString()),
                    180);
            Dimensions dimensions = dimensions(input);
            width = dimensions.width();
            height = dimensions.height();
        }
        media.updateTechnicalMetadata(asset.internalId(), width, height, null);
        store(
                asset,
                thumbnail,
                "THUMBNAIL",
                "default",
                "image/jpeg",
                width,
                height,
                null,
                "thumbnail/default.jpg");
    }

    private void processVideo(MediaAsset asset, Path input, Path directory) throws Exception {
        Dimensions dimensions = dimensions(input);
        Long duration = duration(input);
        media.updateTechnicalMetadata(
                asset.internalId(), dimensions.width(), dimensions.height(), duration);
        if (!media.hasVariant(asset.internalId(), "POSTER", "default")) {
            Path poster = directory.resolve("poster.jpg");
            run(
                    List.of(
                            ffmpeg,
                            "-v",
                            "error",
                            "-y",
                            "-threads",
                            "1",
                            "-ss",
                            "00:00:01",
                            "-i",
                            input.toString(),
                            "-frames:v",
                            "1",
                            "-q:v",
                            "3",
                            poster.toString()),
                    180);
            store(
                    asset,
                    poster,
                    "POSTER",
                    "default",
                    "image/jpeg",
                    dimensions.width(),
                    dimensions.height(),
                    null,
                    "poster/default.jpg");
        }
        if (!media.hasVariant(asset.internalId(), "TRANSCODED", "720p")) {
            Path transcoded = directory.resolve("video-720p.mp4");
            run(
                    List.of(
                            ffmpeg,
                            "-v",
                            "error",
                            "-y",
                            "-threads",
                            "1",
                            "-i",
                            input.toString(),
                            "-vf",
                            "scale=1280:720:force_original_aspect_ratio=decrease",
                            "-c:v",
                            "libx264",
                            "-preset",
                            "veryfast",
                            "-crf",
                            "24",
                            "-c:a",
                            "aac",
                            "-movflags",
                            "+faststart",
                            transcoded.toString()),
                    1800);
            store(
                    asset,
                    transcoded,
                    "TRANSCODED",
                    "720p",
                    "video/mp4",
                    null,
                    null,
                    duration,
                    "transcoded/720p.mp4");
        }
    }

    private void processAudio(MediaAsset asset, Path input, Path directory) throws Exception {
        Long duration = duration(input);
        media.updateTechnicalMetadata(asset.internalId(), null, null, duration);
        if (!media.hasVariant(asset.internalId(), "WAVEFORM", "default")) {
            Path waveform = directory.resolve("waveform.png");
            run(
                    List.of(
                            ffmpeg,
                            "-v",
                            "error",
                            "-y",
                            "-threads",
                            "1",
                            "-i",
                            input.toString(),
                            "-filter_complex",
                            "showwavespic=s=1200x240:colors=#b76d61",
                            "-frames:v",
                            "1",
                            waveform.toString()),
                    300);
            store(
                    asset,
                    waveform,
                    "WAVEFORM",
                    "default",
                    "image/png",
                    1200,
                    240,
                    null,
                    "waveform/default.png");
        }
        if (!media.hasVariant(asset.internalId(), "TRANSCODED", "aac")) {
            Path transcoded = directory.resolve("audio-aac.m4a");
            run(
                    List.of(
                            ffmpeg,
                            "-v",
                            "error",
                            "-y",
                            "-threads",
                            "1",
                            "-i",
                            input.toString(),
                            "-vn",
                            "-c:a",
                            "aac",
                            "-b:a",
                            "160k",
                            transcoded.toString()),
                    900);
            store(
                    asset,
                    transcoded,
                    "TRANSCODED",
                    "aac",
                    "audio/mp4",
                    null,
                    null,
                    duration,
                    "transcoded/aac.m4a");
        }
    }

    private void store(
            MediaAsset asset,
            Path path,
            String type,
            String profile,
            String contentType,
            Integer width,
            Integer height,
            Long duration,
            String suffix)
            throws Exception {
        long size = Files.size(path);
        if (size <= 0) throw new IOException("Generated media variant is empty");
        if (!media.reserveStorage(asset.spaceId(), size)) {
            throw new ApiException(
                    HttpStatus.INSUFFICIENT_STORAGE, "SPACE_QUOTA_EXCEEDED", "空间存储额度不足");
        }
        ObjectStorage storage = storages.writer();
        String key =
                "v3/media/"
                        + asset.spaceId()
                        + "/"
                        + asset.id()
                        + "/derived/"
                        + UUID.randomUUID()
                        + "/"
                        + suffix;
        boolean stored = false;
        boolean releaseNeeded = true;
        try (InputStream input = Files.newInputStream(path)) {
            storage.put(key, input, size, contentType);
            stored = true;
            boolean inserted =
                    media.insertVariant(
                            new MediaRepository.NewVariant(
                                    asset.internalId(),
                                    type,
                                    profile,
                                    storage.provider(),
                                    key,
                                    contentType,
                                    size,
                                    checksum(path),
                                    width,
                                    height,
                                    duration,
                                    "READY"));
            if (!inserted) {
                storage.delete(key);
                stored = false;
                releaseNeeded = false;
                media.releaseStorage(asset.spaceId(), size);
            } else releaseNeeded = false;
        } catch (Exception exception) {
            if (releaseNeeded) {
                try {
                    media.releaseStorage(asset.spaceId(), size);
                } catch (RuntimeException cleanup) {
                    exception.addSuppressed(cleanup);
                }
            }
            if (stored) {
                try {
                    storage.delete(key);
                } catch (IOException cleanup) {
                    exception.addSuppressed(cleanup);
                }
            }
            throw exception;
        }
    }

    private Dimensions dimensions(Path input) throws Exception {
        String output =
                command(
                                List.of(
                                        ffprobe,
                                        "-v",
                                        "error",
                                        "-select_streams",
                                        "v:0",
                                        "-show_entries",
                                        "stream=width,height",
                                        "-of",
                                        "csv=s=x:p=0",
                                        input.toString()),
                                30)
                        .trim();
        String[] values = output.split("x");
        if (values.length != 2) return new Dimensions(null, null);
        return new Dimensions(Integer.valueOf(values[0]), Integer.valueOf(values[1]));
    }

    private Long duration(Path input) throws Exception {
        String output =
                command(
                                List.of(
                                        ffprobe,
                                        "-v",
                                        "error",
                                        "-show_entries",
                                        "format=duration",
                                        "-of",
                                        "default=noprint_wrappers=1:nokey=1",
                                        input.toString()),
                                30)
                        .trim();
        return Math.max(0, Math.round(Double.parseDouble(output) * 1000));
    }

    private void run(List<String> command, int timeoutSeconds) throws Exception {
        command(command, timeoutSeconds);
    }

    private String command(List<String> command, int timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Media processing timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IOException(output.substring(0, Math.min(output.length(), 1000)));
        }
        return output;
    }

    private void requireTemporarySpace(Path directory) throws IOException {
        FileStore store = Files.getFileStore(directory);
        if (store.getUsableSpace() < minimumFreeBytes) {
            throw new IOException("Insufficient temporary disk space for media processing");
        }
    }

    private byte[] checksum(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        return digest.digest();
    }

    private UUID uuid(JsonNode payload, String name) {
        try {
            return UUID.fromString(payload.path(name).asText());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid media job payload");
        }
    }

    private String extension(String contentType) {
        if (contentType == null) return ".bin";
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "audio/mpeg" -> ".mp3";
            case "audio/ogg" -> ".ogg";
            case "audio/wav" -> ".wav";
            default -> ".bin";
        };
    }

    private void deleteDirectory(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
        } catch (IOException ignored) {
        }
    }

    private record Dimensions(Integer width, Integer height) {}
}
