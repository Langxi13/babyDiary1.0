package com.langxi.babydiary.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.entity.MediaAsset;
import com.langxi.babydiary.mapper.MediaMapper;
import com.langxi.babydiary.storage.ObjectStorage;
import com.langxi.babydiary.storage.StoredObject;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MediaProcessingService {
    private final MediaMapper mapper;
    private final ObjectStorage storage;
    private final ObjectMapper objectMapper;

    @Value("${app.media.processing-enabled:true}")
    private boolean enabled;

    @Value("${app.media.ffmpeg:ffmpeg}")
    private String ffmpeg;

    @Value("${app.media.ffprobe:ffprobe}")
    private String ffprobe;

    @Value("${app.media.tesseract:tesseract}")
    private String tesseract;

    public MediaProcessingService(MediaMapper mapper, ObjectStorage storage, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.media.processing-delay-ms:30000}")
    public void processPending() {
        if (!enabled) return;
        for (MediaAsset asset : mapper.findPending(5)) {
            process(asset);
        }
    }

    private void process(MediaAsset asset) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("baby-diary-media-");
            Path input = directory.resolve("input" + extension(asset.getStorageKey()));
            try (StoredObject object = storage.get(asset.getStorageKey());
                 java.io.InputStream stream = object.stream()) {
                Files.copy(stream, input, StandardCopyOption.REPLACE_EXISTING);
            }
            if ("IMAGE".equals(asset.getMediaType())) processImage(asset, input, directory);
            else if ("AUDIO".equals(asset.getMediaType())) processAudio(asset, input, directory);
            else if ("VIDEO".equals(asset.getMediaType())) processVideo(asset, input, directory);
            asset.setStatus("READY");
            asset.setProcessingError(null);
        } catch (Exception exception) {
            log.warn("媒体派生处理失败: assetId={}, reason={}", asset.getPublicId(), exception.getMessage());
            asset.setStatus("READY");
            asset.setProcessingError(limit(exception.getMessage(), 1000));
        } finally {
            mapper.updateProcessing(asset);
            deleteDirectory(directory);
        }
    }

    private void processImage(MediaAsset asset, Path input, Path directory) throws Exception {
        BufferedImage image = ImageIO.read(input.toFile());
        if (image == null) throw new IOException("无法读取图片");
        asset.setWidth(image.getWidth());
        asset.setHeight(image.getHeight());
        Path thumbnail = directory.resolve("thumbnail.jpg");
        Thumbnails.of(image).size(960, 960).outputFormat("jpg").outputQuality(0.84).toFile(thumbnail.toFile());
        asset.setThumbnailKey(storeDerivative(asset, thumbnail, "thumbnail.jpg", "image/jpeg"));
        try {
            String text = commandOutput(List.of(tesseract, input.toString(), "stdout", "-l", "chi_sim+eng"), 90);
            asset.setOcrText(text == null || text.isBlank() ? null : limit(text.trim(), 200_000));
        } catch (Exception unavailable) {
            asset.setProcessingError("OCR不可用: " + limit(unavailable.getMessage(), 300));
        }
    }

    private void processAudio(MediaAsset asset, Path input, Path directory) throws Exception {
        asset.setDurationSeconds(duration(input));
        Path waveform = directory.resolve("waveform.png");
        run(List.of(ffmpeg, "-v", "error", "-y", "-i", input.toString(), "-filter_complex",
                "showwavespic=s=1200x240:colors=#b76d61", "-frames:v", "1", waveform.toString()), 180);
        asset.setWaveformKey(storeDerivative(asset, waveform, "waveform.png", "image/png"));
    }

    private void processVideo(MediaAsset asset, Path input, Path directory) throws Exception {
        asset.setDurationSeconds(duration(input));
        String dimensions = commandOutput(List.of(ffprobe, "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height", "-of", "csv=s=x:p=0", input.toString()), 30).trim();
        String[] values = dimensions.split("x");
        if (values.length == 2) {
            asset.setWidth(Integer.valueOf(values[0]));
            asset.setHeight(Integer.valueOf(values[1]));
        }
        Path poster = directory.resolve("poster.jpg");
        run(List.of(ffmpeg, "-v", "error", "-y", "-ss", "00:00:01", "-i", input.toString(),
                "-frames:v", "1", "-q:v", "3", poster.toString()), 120);
        asset.setPosterKey(storeDerivative(asset, poster, "poster.jpg", "image/jpeg"));

        Path transcoded = directory.resolve("video-720p.mp4");
        run(List.of(ffmpeg, "-v", "error", "-y", "-i", input.toString(), "-vf",
                "scale=1280:720:force_original_aspect_ratio=decrease", "-c:v", "libx264", "-preset", "veryfast",
                "-crf", "24", "-c:a", "aac", "-movflags", "+faststart", transcoded.toString()), 900);
        asset.setTranscodedKey(storeDerivative(asset, transcoded, "video-720p.mp4", "video/mp4"));
    }

    private int duration(Path input) throws Exception {
        String value = commandOutput(List.of(ffprobe, "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", input.toString()), 30).trim();
        return (int) Math.ceil(Double.parseDouble(value));
    }

    private String storeDerivative(MediaAsset asset, Path path, String name, String contentType) throws IOException {
        String key = asset.getStorageKey() + ".derived/" + name;
        try (java.io.InputStream input = Files.newInputStream(path)) {
            storage.put(key, input, Files.size(path), contentType);
        }
        return key;
    }

    private void run(List<String> command, int timeoutSeconds) throws Exception {
        String output = commandOutput(command, timeoutSeconds);
        if (output != null && output.length() > 5000) log.debug("媒体处理输出: {}", output.substring(0, 5000));
    }

    private String commandOutput(List<String> command, int timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("媒体处理超时");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new IOException(limit(output.trim(), 1000));
        return output;
    }

    private String extension(String key) {
        int index = key.lastIndexOf('.');
        return index < 0 ? ".bin" : key.substring(index);
    }

    private String limit(String value, int max) {
        if (value == null) return "未知错误";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private void deleteDirectory(Path directory) {
        if (directory == null) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}
