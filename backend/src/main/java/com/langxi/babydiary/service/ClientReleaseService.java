package com.langxi.babydiary.service;

import com.langxi.babydiary.config.ClientReleaseProperties;
import com.langxi.babydiary.dto.ClientBootstrapVO;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ClientReleaseService {

    private static final Pattern VERSION_NAME = Pattern.compile("[0-9]+(\\.[0-9]+){1,3}([.-][A-Za-z0-9]+)*");
    private static final Pattern SHA256 = Pattern.compile("[a-fA-F0-9]{64}");
    private static final int MAX_RELEASE_NOTES_LENGTH = 4000;

    private final ClientReleaseProperties properties;

    public ClientReleaseService(ClientReleaseProperties properties) {
        this.properties = properties;
    }

    public ClientBootstrapVO bootstrap() {
        return ClientBootstrapVO.current(normalizeServerVersion(properties.getVersion()), androidUpdate());
    }

    private ClientBootstrapVO.AndroidUpdate androidUpdate() {
        ClientReleaseProperties.Android config = properties.getAndroid();
        String distribution = normalizeDistribution(config.getDistribution());
        String versionName = trim(config.getLatestVersionName());
        String downloadUrl = trim(config.getDownloadUrl());
        String checksum = trim(config.getSha256()).toLowerCase(Locale.ROOT);
        int minimumVersionCode = config.getMinimumVersionCode();

        boolean valid = config.isEnabled()
                && distribution != null
                && minimumVersionCode >= 1
                && config.getLatestVersionCode() >= minimumVersionCode
                && VERSION_NAME.matcher(versionName).matches()
                && isSafeUpdateUrl(downloadUrl)
                && ("PLAY".equals(distribution)
                    || (isApkDownload(downloadUrl) && SHA256.matcher(checksum).matches()));

        if (!valid) {
            return ClientBootstrapVO.AndroidUpdate.disabled();
        }

        return new ClientBootstrapVO.AndroidUpdate(
                true,
                distribution,
                config.getLatestVersionCode(),
                versionName,
                minimumVersionCode,
                downloadUrl,
                "DIRECT".equals(distribution) ? checksum : "",
                limit(trim(config.getReleaseNotes()), MAX_RELEASE_NOTES_LENGTH),
                config.isMandatory()
        );
    }

    private String normalizeServerVersion(String value) {
        String normalized = trim(value);
        return normalized.isEmpty() ? "unknown" : limit(normalized, 64);
    }

    private String normalizeDistribution(String value) {
        String normalized = trim(value).toUpperCase(Locale.ROOT);
        return "DIRECT".equals(normalized) || "PLAY".equals(normalized) ? normalized : null;
    }

    private boolean isSafeUpdateUrl(String value) {
        if (value.isEmpty() || value.indexOf('\\') >= 0) return false;
        try {
            URI uri = URI.create(value);
            if (uri.getFragment() != null || uri.getUserInfo() != null) return false;
            if (uri.isAbsolute()) {
                return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
            }
            return value.startsWith("/")
                    && !value.startsWith("//")
                    && uri.getPath() != null
                    && uri.getPath().equals(uri.normalize().getPath());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isApkDownload(String value) {
        try {
            String path = URI.create(value).getPath();
            return path != null && path.toLowerCase(Locale.ROOT).endsWith(".apk");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
