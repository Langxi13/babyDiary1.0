package com.langxi.babydiary.platform.api;

import com.langxi.babydiary.platform.application.ClientReleaseProperties;
import com.langxi.babydiary.platform.application.ProductVersion;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiContract.ROOT + "/client")
public class ClientBootstrapController {
    private final String serverVersion;
    private final AndroidUpdate android;

    public ClientBootstrapController(
            ClientReleaseProperties releases, ProductVersion productVersion) {
        this.serverVersion = productVersion.value();
        ClientReleaseProperties.Android configured = releases.getAndroid();
        this.android =
                configured.isUsable()
                        ? new AndroidUpdate(
                                true,
                                configured.getDistribution(),
                                configured.getLatestVersionCode(),
                                configured.getLatestVersionName(),
                                configured.getMinimumVersionCode(),
                                configured.getDownloadUrl(),
                                configured.getSha256(),
                                configured.getReleaseNotes(),
                                configured.isMandatory())
                        : AndroidUpdate.disabled();
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<Bootstrap> bootstrap() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        new Bootstrap(
                                ApiContract.VERSION,
                                "BEARER_REFRESH_COOKIE",
                                serverVersion,
                                new UploadPolicy(
                                        25L * 1024 * 1024,
                                        80_000_000L,
                                        50,
                                        List.of(
                                                "image/gif",
                                                "image/heic",
                                                "image/heif",
                                                "image/jpeg",
                                                "image/png",
                                                "image/webp")),
                                android));
    }

    public record Bootstrap(
            int apiVersion,
            String nativeSessionMode,
            String serverVersion,
            UploadPolicy upload,
            AndroidUpdate androidUpdate) {}

    public record UploadPolicy(
            long maxImageBytes,
            long maxImagePixels,
            int maxDiaryImages,
            List<String> acceptedImageTypes) {}

    public record AndroidUpdate(
            boolean enabled,
            String distribution,
            int latestVersionCode,
            String latestVersionName,
            int minimumVersionCode,
            String downloadUrl,
            String sha256,
            String releaseNotes,
            boolean mandatory) {
        static AndroidUpdate disabled() {
            return new AndroidUpdate(false, "", 0, "", 1, "", "", "", false);
        }
    }
}
