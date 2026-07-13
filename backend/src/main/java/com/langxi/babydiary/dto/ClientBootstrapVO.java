package com.langxi.babydiary.dto;

import com.langxi.babydiary.service.ImageStorageService;
import com.langxi.babydiary.validation.DiaryRequestValidator;

import java.util.List;

public record ClientBootstrapVO(int apiVersion,
                                String nativeSessionMode,
                                UploadPolicy upload) {

    public static ClientBootstrapVO current() {
        List<String> imageTypes = ImageStorageService.SUPPORTED_IMAGE_TYPES.stream()
                .sorted()
                .toList();
        return new ClientBootstrapVO(
                2,
                "COOKIE",
                new UploadPolicy(ImageStorageService.MAX_IMAGE_BYTES, DiaryRequestValidator.MAX_IMAGES, imageTypes)
        );
    }

    public record UploadPolicy(long maxImageBytes,
                               int maxDiaryImages,
                               List<String> acceptedImageTypes) {
    }
}
