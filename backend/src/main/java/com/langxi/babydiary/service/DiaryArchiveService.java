package com.langxi.babydiary.service;

import com.langxi.babydiary.entity.MediaAsset;
import com.langxi.babydiary.mapper.MediaMapper;
import com.langxi.babydiary.storage.StoredObject;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DiaryArchiveService {
    private final MediaMapper mediaMapper;
    private final MediaService mediaService;

    public DiaryArchiveService(MediaMapper mediaMapper, MediaService mediaService) {
        this.mediaMapper = mediaMapper;
        this.mediaService = mediaService;
    }

    public FileSystemResource exportImagesAsZip(Integer userId, String startDate, String endDate) throws IOException {
        var assets = mediaMapper.findByUserAndDateRange(userId, startDate, endDate);
        if (assets.isEmpty()) return null;

        Path zipFile = Files.createTempFile("diary_images", ".zip");
        boolean exported = false;
        Set<String> entryNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (MediaAsset asset : assets) {
                String preferred = asset.getOriginalFilename() == null || asset.getOriginalFilename().isBlank()
                        ? asset.getPublicId() + extensionOf(asset.getStorageKey()) : asset.getOriginalFilename();
                try (StoredObject object = mediaService.openOriginalForExport(asset)) {
                    zip.putNextEntry(new ZipEntry(uniqueEntryName(preferred, entryNames)));
                    object.stream().transferTo(zip);
                    zip.closeEntry();
                    exported = true;
                }
            }
        } catch (IOException exception) {
            Files.deleteIfExists(zipFile);
            throw exception;
        }
        if (!exported) {
            Files.deleteIfExists(zipFile);
            return null;
        }
        return new TemporaryFileResource(zipFile);
    }

    private String uniqueEntryName(String fileName, Set<String> existingNames) {
        if (existingNames.add(fileName)) return fileName;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        int index = 2;
        String candidate;
        do candidate = base + "_" + index++ + extension; while (!existingNames.add(candidate));
        return candidate;
    }

    private String extensionOf(String value) {
        int dot = value == null ? -1 : value.lastIndexOf('.');
        return dot < 0 ? "" : value.substring(dot);
    }
}
