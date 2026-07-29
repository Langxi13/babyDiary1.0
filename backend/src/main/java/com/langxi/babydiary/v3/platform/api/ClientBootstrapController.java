package com.langxi.babydiary.v3.platform.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v3/client")
public class ClientBootstrapController {
    private final String serverVersion;
    private final AndroidUpdate android;

    public ClientBootstrapController(
            @Value("${app.release.version:3.0.0}") String serverVersion,
            @Value("${app.release.android.enabled:false}") boolean enabled,
            @Value("${app.release.android.distribution:DIRECT}") String distribution,
            @Value("${app.release.android.latest-version-code:0}") int latestVersionCode,
            @Value("${app.release.android.latest-version-name:}") String latestVersionName,
            @Value("${app.release.android.minimum-version-code:1}") int minimumVersionCode,
            @Value("${app.release.android.download-url:}") String downloadUrl,
            @Value("${app.release.android.sha256:}") String sha256,
            @Value("${app.release.android.release-notes:}") String releaseNotes,
            @Value("${app.release.android.mandatory:false}") boolean mandatory) {
        this.serverVersion = serverVersion == null || serverVersion.isBlank() ? "3.0.0" : serverVersion.trim();
        boolean valid = enabled && latestVersionCode >= Math.max(1, minimumVersionCode)
                && latestVersionName != null && !latestVersionName.isBlank()
                && downloadUrl != null && (downloadUrl.startsWith("/") || downloadUrl.startsWith("https://"));
        this.android = valid ? new AndroidUpdate(true, distribution, latestVersionCode, latestVersionName,
                minimumVersionCode, downloadUrl, sha256 == null ? "" : sha256,
                releaseNotes == null ? "" : releaseNotes, mandatory) : AndroidUpdate.disabled();
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<Bootstrap> bootstrap() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Bootstrap(3, "BEARER_REFRESH_COOKIE",
                serverVersion, new UploadPolicy(10L * 1024 * 1024, 50,
                List.of("image/gif", "image/jpeg", "image/png", "image/webp")), android));
    }

    public record Bootstrap(int apiVersion, String nativeSessionMode, String serverVersion,
                            UploadPolicy upload, AndroidUpdate androidUpdate) {}
    public record UploadPolicy(long maxImageBytes, int maxDiaryImages, List<String> acceptedImageTypes) {}
    public record AndroidUpdate(boolean enabled, String distribution, int latestVersionCode, String latestVersionName,
                                int minimumVersionCode, String downloadUrl, String sha256, String releaseNotes,
                                boolean mandatory) {
        static AndroidUpdate disabled() { return new AndroidUpdate(false, "", 0, "", 1, "", "", "", false); }
    }
}
