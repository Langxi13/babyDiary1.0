package com.langxi.babydiary.media.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SignedMediaMapper {
    VariantRow resolve(
            @Param("spaceId") byte[] spaceId,
            @Param("assetId") byte[] assetId,
            @Param("type") String type,
            @Param("profile") String profile,
            @Param("source") String source,
            @Param("contextId") byte[] contextId,
            @Param("accountId") long accountId);

    final class VariantRow {
        private boolean protectedContent;
        private String variantType;
        private String profile;
        private String storageProvider;
        private String storageKey;
        private String contentType;
        private long sizeBytes;
        private byte[] checksumSha256;
        private Integer width;
        private Integer height;
        private Long durationMillis;
        private Double qualityScore;
        private String status;

        public VariantRow() {}

        public boolean protectedContent() {
            return protectedContent;
        }

        public String variantType() {
            return variantType;
        }

        public String profile() {
            return profile;
        }

        public String storageProvider() {
            return storageProvider;
        }

        public String storageKey() {
            return storageKey;
        }

        public String contentType() {
            return contentType;
        }

        public long sizeBytes() {
            return sizeBytes;
        }

        public byte[] checksumSha256() {
            return checksumSha256;
        }

        public Integer width() {
            return width;
        }

        public Integer height() {
            return height;
        }

        public Long durationMillis() {
            return durationMillis;
        }

        public Double qualityScore() {
            return qualityScore;
        }

        public String status() {
            return status;
        }

        public void setProtectedContent(boolean protectedContent) {
            this.protectedContent = protectedContent;
        }

        public void setVariantType(String variantType) {
            this.variantType = variantType;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }

        public void setStorageProvider(String storageProvider) {
            this.storageProvider = storageProvider;
        }

        public void setStorageKey(String storageKey) {
            this.storageKey = storageKey;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public void setSizeBytes(long sizeBytes) {
            this.sizeBytes = sizeBytes;
        }

        public void setChecksumSha256(byte[] checksumSha256) {
            this.checksumSha256 = checksumSha256;
        }

        public void setWidth(Integer width) {
            this.width = width;
        }

        public void setHeight(Integer height) {
            this.height = height;
        }

        public void setDurationMillis(Long durationMillis) {
            this.durationMillis = durationMillis;
        }

        public void setQualityScore(Double qualityScore) {
            this.qualityScore = qualityScore;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
