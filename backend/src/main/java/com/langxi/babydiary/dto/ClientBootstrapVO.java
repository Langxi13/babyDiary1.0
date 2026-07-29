package com.langxi.babydiary.dto;

import com.langxi.babydiary.validation.DiaryRequestValidator;

import java.util.List;

public record ClientBootstrapVO(int apiVersion,
                                String nativeSessionMode,
                                String serverVersion,
                                UploadPolicy upload,
                                AndroidUpdate androidUpdate) {

    public static ClientBootstrapVO current(String serverVersion, AndroidUpdate androidUpdate) {
        List<String> imageTypes = List.of("image/gif", "image/jpeg", "image/png", "image/webp");
        return new ClientBootstrapVO(
                2,
                "COOKIE",
                serverVersion,
                new UploadPolicy(10L * 1024L * 1024L, DiaryRequestValidator.MAX_IMAGES, imageTypes),
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
