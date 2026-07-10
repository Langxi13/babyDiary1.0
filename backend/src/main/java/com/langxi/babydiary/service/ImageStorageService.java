package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.util.ImageCompressor;
import com.langxi.babydiary.util.ThumbnailGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ImageStorageService {

    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );

    @Value("${diaryFilePath}")
    private String uploadDir;

    public ImageStorageService() {
    }

    ImageStorageService(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String storeImage(MultipartFile file, String prefix, boolean createThumbnail) throws IOException {
        byte[] originalBytes = validateAndRead(file);
        String fileName = safePrefix(prefix) + UUID.randomUUID().toString().replace("-", "")
                + extensionFor(file.getContentType());
        Path path = resolveImagePath(fileName);

        try {
            Files.createDirectories(path.getParent());
            byte[] imageData = ImageCompressor.shouldCompress(file)
                    ? ImageCompressor.compressImage(originalBytes, file.getContentType())
                    : originalBytes;
            Files.write(path, imageData, StandardOpenOption.CREATE_NEW);
            if (createThumbnail) {
                ThumbnailGenerator.createThumbnail(uploadRoot(), fileName);
            }
        } catch (IOException | RuntimeException e) {
            deleteQuietly(fileName);
            throw e;
        }

        registerRollbackCleanup(fileName);
        return fileName;
    }

    public void deleteAfterCommit(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(imagePath);
                }
            });
            return;
        }
        deleteQuietly(imagePath);
    }

    public Path resolveImagePath(String imagePath) {
        Path root = uploadRoot();
        Path path = root.resolve(imagePath).normalize();
        if (!path.startsWith(root)) {
            throw new InvalidPathException(imagePath, "Image path escapes upload directory");
        }
        return path;
    }

    public boolean isOwnedPath(String imagePath, String prefix) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return false;
        }
        try {
            Path path = Paths.get(imagePath).normalize();
            return !path.isAbsolute()
                    && path.getNameCount() == 1
                    && path.getFileName().toString().startsWith(safePrefix(prefix));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] validateAndRead(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "图片文件不能为空");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!EXTENSIONS_BY_CONTENT_TYPE.containsKey(contentType)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "仅支持 JPEG、PNG、GIF 和 WebP 图片");
        }
        byte[] bytes = file.getBytes();
        if (!hasExpectedSignature(bytes, contentType)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "图片文件内容与格式不匹配");
        }
        return bytes;
    }

    private boolean hasExpectedSignature(byte[] bytes, String contentType) {
        if ("image/jpeg".equals(contentType) || "image/jpg".equals(contentType)) {
            return bytes.length >= 3
                    && unsigned(bytes[0]) == 0xff
                    && unsigned(bytes[1]) == 0xd8
                    && unsigned(bytes[2]) == 0xff;
        }
        if ("image/png".equals(contentType)) {
            int[] signature = {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            return startsWith(bytes, signature);
        }
        if ("image/gif".equals(contentType)) {
            return startsWith(bytes, new int[]{0x47, 0x49, 0x46, 0x38, 0x37, 0x61})
                    || startsWith(bytes, new int[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61});
        }
        if ("image/webp".equals(contentType)) {
            return bytes.length >= 12
                    && startsWith(bytes, new int[]{0x52, 0x49, 0x46, 0x46})
                    && unsigned(bytes[8]) == 0x57
                    && unsigned(bytes[9]) == 0x45
                    && unsigned(bytes[10]) == 0x42
                    && unsigned(bytes[11]) == 0x50;
        }
        return false;
    }

    private boolean startsWith(byte[] bytes, int[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (unsigned(bytes[i]) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private String extensionFor(String contentType) {
        return EXTENSIONS_BY_CONTENT_TYPE.get(normalizeContentType(contentType));
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String safePrefix(String prefix) {
        if (prefix == null || !prefix.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid image filename prefix");
        }
        return prefix;
    }

    private Path uploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private void registerRollbackCleanup(String imagePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(imagePath);
                }
            }
        });
    }

    private void deleteQuietly(String imagePath) {
        try {
            Files.deleteIfExists(resolveImagePath(imagePath));
            ThumbnailGenerator.deleteThumbnail(uploadRoot(), imagePath);
        } catch (IOException | RuntimeException e) {
            log.warn("图片文件清理失败: imagePath={}, reason={}", imagePath, e.getMessage());
        }
    }
}
