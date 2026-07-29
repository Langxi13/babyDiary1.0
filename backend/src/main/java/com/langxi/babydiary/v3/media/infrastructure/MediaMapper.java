package com.langxi.babydiary.v3.media.infrastructure;

import com.langxi.babydiary.v3.media.application.MediaRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MediaMapper {
    List<MediaRow> findPage(@Param("query") MediaRepository.Query query);

    List<MediaRow> findByPublicId(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId,
                                  @Param("accountId") long accountId);

    List<MediaRow> findByPublicIds(@Param("spaceId") long spaceId, @Param("publicIds") List<byte[]> publicIds,
                                   @Param("accountId") long accountId);

    VariantRow findVariant(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId,
                           @Param("type") String type, @Param("profile") String profile,
                           @Param("accountId") long accountId);

    VariantRow findPublicVariant(@Param("spaceId") byte[] spaceId, @Param("publicId") byte[] publicId,
                                 @Param("type") String type, @Param("profile") String profile);

    @Insert("""
            INSERT INTO media_asset(public_id,space_id,owner_id,media_type,original_filename,caption,taken_at,
              access_scope,library_visible,status,created_at,updated_at)
            VALUES(#{publicId},#{spaceId},#{ownerId},#{mediaType},#{originalFilename},#{caption},#{takenAt},
              #{accessScope},#{libraryVisible},#{status},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "assetId")
    void insertAsset(AssetInsert row);

    @Insert("""
            INSERT INTO media_variant(asset_id,variant_type,profile,storage_provider,storage_key,content_type,
              size_bytes,checksum_sha256,status,created_at,updated_at)
            VALUES(#{assetId},#{type},#{profile},#{storageProvider},#{storageKey},#{contentType},
              #{sizeBytes},#{checksumSha256},#{status},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    void insertVariant(MediaRepository.NewVariant variant);

    @Update("""
            UPDATE space_storage_usage u JOIN diary_space s ON s.space_id=u.space_id
            SET u.used_bytes=u.used_bytes+#{sizeBytes},u.updated_at=UTC_TIMESTAMP(6)
            WHERE u.space_id=#{spaceId} AND u.used_bytes+#{sizeBytes}<=s.storage_quota_bytes
            """)
    int reserveStorage(@Param("spaceId") long spaceId, @Param("sizeBytes") long sizeBytes);

    @Update("""
            UPDATE space_storage_usage SET used_bytes=GREATEST(used_bytes-#{sizeBytes},0),updated_at=UTC_TIMESTAMP(6)
            WHERE space_id=#{spaceId}
            """)
    void releaseStorage(@Param("spaceId") long spaceId, @Param("sizeBytes") long sizeBytes);

    @Update("""
            UPDATE media_asset SET deleted_at=#{deletedAt},updated_at=UTC_TIMESTAMP(6)
            WHERE space_id=#{spaceId} AND public_id=#{publicId} AND owner_id=#{accountId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId,
                   @Param("accountId") long accountId, @Param("deletedAt") LocalDateTime deletedAt);

    @Update("UPDATE media_asset SET caption=#{caption},taken_at=#{takenAt},access_scope=#{accessScope}," +
            "library_visible=#{libraryVisible},updated_at=UTC_TIMESTAMP(6) WHERE space_id=#{spaceId} " +
            "AND public_id=#{publicId} AND owner_id=#{accountId} AND deleted_at IS NULL")
    int updateMetadata(@Param("spaceId") long spaceId,@Param("publicId") byte[] publicId,
                       @Param("accountId") long accountId,@Param("caption") String caption,
                       @Param("takenAt") LocalDateTime takenAt,@Param("accessScope") String accessScope,
                       @Param("libraryVisible") boolean libraryVisible);

    final class AssetInsert {
        private Long assetId;
        private final byte[] publicId;
        private final long spaceId;
        private final long ownerId;
        private final String mediaType;
        private final String originalFilename;
        private final String caption;
        private final LocalDateTime takenAt;
        private final String accessScope;
        private final boolean libraryVisible;
        private final String status;

        public AssetInsert(byte[] publicId, long spaceId, long ownerId, String mediaType, String originalFilename,
                           String caption, LocalDateTime takenAt, String accessScope, boolean libraryVisible,
                           String status) {
            this.publicId = publicId;
            this.spaceId = spaceId;
            this.ownerId = ownerId;
            this.mediaType = mediaType;
            this.originalFilename = originalFilename;
            this.caption = caption;
            this.takenAt = takenAt;
            this.accessScope = accessScope;
            this.libraryVisible = libraryVisible;
            this.status = status;
        }

        public Long getAssetId() { return assetId; }
        public void setAssetId(Long assetId) { this.assetId = assetId; }
        public byte[] getPublicId() { return publicId; }
        public long getSpaceId() { return spaceId; }
        public long getOwnerId() { return ownerId; }
        public String getMediaType() { return mediaType; }
        public String getOriginalFilename() { return originalFilename; }
        public String getCaption() { return caption; }
        public LocalDateTime getTakenAt() { return takenAt; }
        public String getAccessScope() { return accessScope; }
        public boolean isLibraryVisible() { return libraryVisible; }
        public String getStatus() { return status; }
    }

    final class MediaRow {
        private long assetId;
        private byte[] publicId;
        private byte[] spacePublicId;
        private long ownerId;
        private String mediaType;
        private String originalFilename;
        private String caption;
        private LocalDateTime takenAt;
        private String accessScope;
        private boolean libraryVisible;
        private String assetStatus;
        private LocalDateTime assetCreatedAt;
        private LocalDateTime assetUpdatedAt;
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
        private String variantStatus;

        public MediaRow() {
        }

        public long assetId() { return assetId; }
        public byte[] publicId() { return publicId; }
        public byte[] spacePublicId() { return spacePublicId; }
        public long ownerId() { return ownerId; }
        public String mediaType() { return mediaType; }
        public String originalFilename() { return originalFilename; }
        public String caption() { return caption; }
        public LocalDateTime takenAt() { return takenAt; }
        public String accessScope() { return accessScope; }
        public boolean libraryVisible() { return libraryVisible; }
        public String assetStatus() { return assetStatus; }
        public LocalDateTime assetCreatedAt() { return assetCreatedAt; }
        public LocalDateTime assetUpdatedAt() { return assetUpdatedAt; }
        public String variantType() { return variantType; }
        public String profile() { return profile; }
        public String storageProvider() { return storageProvider; }
        public String storageKey() { return storageKey; }
        public String contentType() { return contentType; }
        public long sizeBytes() { return sizeBytes; }
        public byte[] checksumSha256() { return checksumSha256; }
        public Integer width() { return width; }
        public Integer height() { return height; }
        public Long durationMillis() { return durationMillis; }
        public String variantStatus() { return variantStatus; }

        public void setAssetId(long assetId) { this.assetId = assetId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setSpacePublicId(byte[] spacePublicId) { this.spacePublicId = spacePublicId; }
        public void setOwnerId(long ownerId) { this.ownerId = ownerId; }
        public void setMediaType(String mediaType) { this.mediaType = mediaType; }
        public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
        public void setCaption(String caption) { this.caption = caption; }
        public void setTakenAt(LocalDateTime takenAt) { this.takenAt = takenAt; }
        public void setAccessScope(String accessScope) { this.accessScope = accessScope; }
        public void setLibraryVisible(boolean libraryVisible) { this.libraryVisible = libraryVisible; }
        public void setAssetStatus(String assetStatus) { this.assetStatus = assetStatus; }
        public void setAssetCreatedAt(LocalDateTime assetCreatedAt) { this.assetCreatedAt = assetCreatedAt; }
        public void setAssetUpdatedAt(LocalDateTime assetUpdatedAt) { this.assetUpdatedAt = assetUpdatedAt; }
        public void setVariantType(String variantType) { this.variantType = variantType; }
        public void setProfile(String profile) { this.profile = profile; }
        public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }
        public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
        public void setChecksumSha256(byte[] checksumSha256) { this.checksumSha256 = checksumSha256; }
        public void setWidth(Integer width) { this.width = width; }
        public void setHeight(Integer height) { this.height = height; }
        public void setDurationMillis(Long durationMillis) { this.durationMillis = durationMillis; }
        public void setVariantStatus(String variantStatus) { this.variantStatus = variantStatus; }
    }

    final class VariantRow {
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
        private String status;

        public VariantRow() {
        }

        public String variantType() { return variantType; }
        public String profile() { return profile; }
        public String storageProvider() { return storageProvider; }
        public String storageKey() { return storageKey; }
        public String contentType() { return contentType; }
        public long sizeBytes() { return sizeBytes; }
        public byte[] checksumSha256() { return checksumSha256; }
        public Integer width() { return width; }
        public Integer height() { return height; }
        public Long durationMillis() { return durationMillis; }
        public String status() { return status; }

        public void setVariantType(String variantType) { this.variantType = variantType; }
        public void setProfile(String profile) { this.profile = profile; }
        public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }
        public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
        public void setChecksumSha256(byte[] checksumSha256) { this.checksumSha256 = checksumSha256; }
        public void setWidth(Integer width) { this.width = width; }
        public void setHeight(Integer height) { this.height = height; }
        public void setDurationMillis(Long durationMillis) { this.durationMillis = durationMillis; }
        public void setStatus(String status) { this.status = status; }
    }
}
