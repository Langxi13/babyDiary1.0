package com.langxi.babydiary.dto;

import com.langxi.babydiary.service.ImageStorageService;
import com.langxi.babydiary.validation.DiaryRequestValidator;

import java.util.List;

public record ClientBootstrapVO(int apiVersion,
                                String nativeSessionMode,
                                String serverVersion,
                                UploadPolicy upload,
                                AndroidUpdate androidUpdate) {

    public static ClientBootstrapVO current(String serverVersion, AndroidUpdate androidUpdate) {
        List<String> imageTypes = ImageStorageService.SUPPORTED_IMAGE_TYPES.stream()
                .sorted()
                .toList();
        return new ClientBootstrapVO(
                2,
                "COOKIE",
                serverVersion,
                new UploadPolicy(ImageStorageService.MAX_IMAGE_BYTES, DiaryRequestValidator.MAX_IMAGES, imageTypes),
                androidUpdate
        );
    }

    public record UploadPolicy(long maxImageBytes,
                               int maxDiaryImages,
                               List<String> acceptedImageTypes) {
    }

    public record AndroidUpdate(boolean enabled,
                                String distribution,
                                int latestVersionCode,
                                String latestVersionName,
                                int minimumVersionCode,
                                String downloadUrl,
                                String sha256,
                                String releaseNotes,
                                boolean mandatory) {

        public static AndroidUpdate disabled() {
            return new AndroidUpdate(false, "", 0, "", 1, "", "", "", false);
        }
    }
}
