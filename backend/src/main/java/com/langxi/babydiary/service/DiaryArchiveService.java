package com.langxi.babydiary.service;

import com.langxi.babydiary.mapper.DiaryImageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class DiaryArchiveService {

    @Autowired
    private DiaryImageMapper diaryImageMapper;

    @Autowired
    private ImageStorageService imageStorageService;

    public FileSystemResource exportImagesAsZip(Integer userId, String startDate, String endDate) throws IOException {
        List<String> imagePaths = diaryImageMapper.findImagePathsByUserIdAndDateRange(userId, startDate, endDate);
        if (imagePaths.isEmpty()) {
            return null;
        }

        Path zipFile = Files.createTempFile("diary_images", ".zip");
        boolean hasExportedFile = false;
        Set<String> entryNames = new HashSet<>();
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (String imagePath : imagePaths) {
                if (imagePath == null || imagePath.trim().isEmpty()) {
                    continue;
                }
                for (String pathItem : imagePath.split(",")) {
                    Path path;
                    try {
                        path = imageStorageService.resolveImagePath(pathItem.trim());
                    } catch (IllegalArgumentException e) {
                        log.warn("跳过无效的日记图片路径: imagePath={}", pathItem);
                        continue;
                    }
                    if (!Files.isRegularFile(path)) {
                        continue;
                    }
                    zipOut.putNextEntry(new ZipEntry(uniqueEntryName(path.getFileName().toString(), entryNames)));
                    Files.copy(path, zipOut);
                    zipOut.closeEntry();
                    hasExportedFile = true;
                }
            }
        }
        if (!hasExportedFile) {
            Files.deleteIfExists(zipFile);
            return null;
        }
        return new TemporaryFileResource(zipFile);
    }

    private String uniqueEntryName(String fileName, Set<String> existingNames) {
        if (existingNames.add(fileName)) {
            return fileName;
        }
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";
        int index = 2;
        String candidate;
        do {
            candidate = baseName + "_" + index++ + extension;
        } while (!existingNames.add(candidate));
        return candidate;
    }
}
