package com.langxi.babydiary.platform.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.release")
public class ClientReleaseProperties {
    private Android android = new Android();

    public Android getAndroid() {
        return android;
    }

    public void setAndroid(Android android) {
        this.android = android == null ? new Android() : android;
    }

    public static final class Android {
        private boolean enabled;
        private String distribution = "DIRECT";
        private int latestVersionCode;
        private String latestVersionName = "";
        private int minimumVersionCode = 1;
        private String downloadUrl = "";
        private String sha256 = "";
        private String releaseNotes = "";
        private boolean mandatory;

        public boolean isUsable() {
            return enabled
                    && latestVersionCode >= Math.max(1, minimumVersionCode)
                    && !latestVersionName.isBlank()
                    && (downloadUrl.startsWith("/") || downloadUrl.startsWith("https://"));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDistribution() {
            return distribution;
        }

        public void setDistribution(String distribution) {
            this.distribution = normalized(distribution);
        }

        public int getLatestVersionCode() {
            return latestVersionCode;
        }

        public void setLatestVersionCode(int latestVersionCode) {
            this.latestVersionCode = latestVersionCode;
        }

        public String getLatestVersionName() {
            return latestVersionName;
        }

        public void setLatestVersionName(String latestVersionName) {
            this.latestVersionName = normalized(latestVersionName);
        }

        public int getMinimumVersionCode() {
            return minimumVersionCode;
        }

        public void setMinimumVersionCode(int minimumVersionCode) {
            this.minimumVersionCode = Math.max(1, minimumVersionCode);
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public void setDownloadUrl(String downloadUrl) {
            this.downloadUrl = normalized(downloadUrl);
        }

        public String getSha256() {
            return sha256;
        }

        public void setSha256(String sha256) {
            this.sha256 = normalized(sha256);
        }

        public String getReleaseNotes() {
            return releaseNotes;
        }

        public void setReleaseNotes(String releaseNotes) {
            this.releaseNotes = normalized(releaseNotes);
        }

        public boolean isMandatory() {
            return mandatory;
        }

        public void setMandatory(boolean mandatory) {
            this.mandatory = mandatory;
        }

        private String normalized(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
