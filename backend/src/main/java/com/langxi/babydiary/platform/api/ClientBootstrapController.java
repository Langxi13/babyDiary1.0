package com.langxi.babydiary.platform.api;

import com.langxi.babydiary.platform.application.ClientReleaseProperties;
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

    public ClientBootstrapController(ClientReleaseProperties releases) {
        String configuredVersion = releases.getVersion();
        this.serverVersion = configuredVersion == null || configuredVersion.isBlank()
                ? "3.0.0" : configuredVersion.trim();
        ClientReleaseProperties.Android configured = releases.getAndroid();
        this.android = configured.isUsable()
                ? new AndroidUpdate(true, configured.getDistribution(), configured.getLatestVersionCode(),
                configured.getLatestVersionName(), configured.getMinimumVersionCode(), configured.getDownloadUrl(),
                configured.getSha256(), configured.getReleaseNotes(), configured.isMandatory())
                : AndroidUpdate.disabled();
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
