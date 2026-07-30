package com.langxi.babydiary.media.infrastructure;

import com.langxi.babydiary.media.application.MediaRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MediaMapper {
    List<MediaRow> findPage(@Param("query") MediaRepository.Query query);

    List<MediaRow> findByPublicId(
            @Param("spaceId") long spaceId,
            @Param("publicId") byte[] publicId,
            @Param("accountId") long accountId);

    List<MediaRow> findByPublicIds(
            @Param("spaceId") long spaceId,
            @Param("publicIds") List<byte[]> publicIds,
            @Param("accountId") long accountId);

    List<MediaRow> findByPublicIdsInSpace(
            @Param("spaceId") long spaceId, @Param("publicIds") List<byte[]> publicIds);

    List<MediaRow> findInSpace(
            @Param("spaceId") byte[] spaceId,
            @Param("publicId") byte[] publicId,
            @Param("includeDeleted") boolean includeDeleted);

    VariantRow findVariant(
            @Param("spaceId") long spaceId,
            @Param("publicId") byte[] publicId,
            @Param("type") String type,
            @Param("profile") String profile,
            @Param("accountId") long accountId);

    VariantRow findPreferredVariant(
            @Param("spaceId") long spaceId,
            @Param("publicId") byte[] publicId,
            @Param("type") String type,
            @Param("accountId") long accountId);

    VariantRow findPublicVariant(
            @Param("spaceId") byte[] spaceId,
            @Param("publicId") byte[] publicId,
            @Param("type") String type,
            @Param("profile") String profile);

    VariantRow findPreferredPublicVariant(
            @Param("spaceId") byte[] spaceId,
            @Param("publicId") byte[] publicId,
            @Param("type") String type);

    @Insert(
            """
            INSERT INTO media_asset(public_id,space_id,owner_id,media_type,original_filename,caption,taken_at,
              access_scope,library_visible,status,created_at,updated_at)
            VALUES(#{publicId},#{spaceId},#{ownerId},#{mediaType},#{originalFilename},#{caption},#{takenAt},
              #{accessScope},#{libraryVisible},#{status},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "assetId")
    void insertAsset(AssetInsert row);

    @Insert(
            """
            INSERT IGNORE INTO media_variant(asset_id,variant_type,profile,storage_provider,storage_key,content_type,
              size_bytes,checksum_sha256,width,height,duration_millis,status,created_at,updated_at)
            VALUES(#{assetId},#{type},#{profile},#{storageProvider},#{storageKey},#{contentType},
              #{sizeBytes},#{checksumSha256},#{width},#{height},#{durationMillis},#{status},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    int insertVariant(MediaRepository.NewVariant variant);

    @Update(
            """
            UPDATE space_storage_usage u JOIN diary_space s ON s.space_id=u.space_id
            SET u.used_bytes=u.used_bytes+#{sizeBytes},u.updated_at=UTC_TIMESTAMP(6)
            WHERE u.space_id=#{spaceId} AND u.used_bytes+#{sizeBytes}<=s.storage_quota_bytes
            """)
    int reserveStorage(@Param("spaceId") long spaceId, @Param("sizeBytes") long sizeBytes);

    @Update(
            """
            UPDATE space_storage_usage u JOIN diary_space s ON s.space_id=u.space_id
            SET u.used_bytes=u.used_bytes+#{sizeBytes},u.updated_at=UTC_TIMESTAMP(6)
            WHERE s.public_id=#{spaceId} AND u.used_bytes+#{sizeBytes}<=s.storage_quota_bytes
            """)
    int reserveStorageByPublicId(
            @Param("spaceId") byte[] spaceId, @Param("sizeBytes") long sizeBytes);

    @Update(
            """
            UPDATE space_storage_usage SET used_bytes=GREATEST(used_bytes-#{sizeBytes},0),updated_at=UTC_TIMESTAMP(6)
            WHERE space_id=#{spaceId}
            """)
    void releaseStorage(@Param("spaceId") long spaceId, @Param("sizeBytes") long sizeBytes);

    @Update(
            """
            UPDATE space_storage_usage u JOIN diary_space s ON s.space_id=u.space_id
            SET u.used_bytes=GREATEST(u.used_bytes-#{sizeBytes},0),u.updated_at=UTC_TIMESTAMP(6)
            WHERE s.public_id=#{spaceId}
            """)
    void releaseStorageByPublicId(
            @Param("spaceId") byte[] spaceId, @Param("sizeBytes") long sizeBytes);

    @Update(
            """
            UPDATE media_asset SET deleted_at=#{deletedAt},updated_at=UTC_TIMESTAMP(6)
            WHERE space_id=#{spaceId} AND public_id=#{publicId} AND owner_id=#{accountId} AND deleted_at IS NULL
            """)
    int softDelete(
            @Param("spaceId") long spaceId,
            @Param("publicId") byte[] publicId,
            @Param("accountId") long accountId,
            @Param("deletedAt") LocalDateTime deletedAt);

    @Update(
            """
            UPDATE media_asset SET status='DELETE_PENDING',deleted_at=#{deletedAt},updated_at=UTC_TIMESTAMP(6)
            WHERE space_id=#{spaceId} AND public_id=#{publicId} AND owner_id=#{accountId}
              AND status='READY' AND deleted_at IS NULL
            """)
    int markDeletePending(
            @Param("spaceId") long spaceId,
            @Param("publicId") byte[] publicId,
            @Param("accountId") long accountId,
            @Param("deletedAt") LocalDateTime deletedAt);

    @Update(
            "UPDATE media_variant SET deleted_at=COALESCE(deleted_at,#{deletedAt}),updated_at=UTC_TIMESTAMP(6) WHERE asset_id=#{assetId}")
    void markVariantsDeleted(
            @Param("assetId") long assetId, @Param("deletedAt") LocalDateTime deletedAt);

    @Update(
            "UPDATE media_asset SET status='DELETED',deleted_at=COALESCE(deleted_at,#{deletedAt}),updated_at=UTC_TIMESTAMP(6) WHERE asset_id=#{assetId}")
    void markAssetDeleted(
            @Param("assetId") long assetId, @Param("deletedAt") LocalDateTime deletedAt);

    @Update(
            "UPDATE media_asset SET status='FAILED',deleted_at=#{failedAt},updated_at=UTC_TIMESTAMP(6) WHERE asset_id=#{assetId}")
    void failUpload(@Param("assetId") long assetId, @Param("failedAt") LocalDateTime failedAt);

    @Update(
            "UPDATE media_asset SET status='READY',updated_at=UTC_TIMESTAMP(6) WHERE asset_id=#{assetId} AND status IN ('UPLOADING','PROCESSING')")
    void markReady(long assetId);

    @Update(
            """
            UPDATE media_variant SET width=COALESCE(width,#{width}),height=COALESCE(height,#{height}),
              duration_millis=COALESCE(duration_millis,#{durationMillis}),updated_at=UTC_TIMESTAMP(6)
            WHERE asset_id=#{assetId} AND variant_type='ORIGINAL' AND status='READY' AND deleted_at IS NULL
            """)
    void updateTechnicalMetadata(
            @Param("assetId") long assetId,
            @Param("width") Integer width,
            @Param("height") Integer height,
            @Param("durationMillis") Long durationMillis);

    @org.apache.ibatis.annotations.Select(
            "SELECT COUNT(*)>0 FROM media_variant WHERE asset_id=#{assetId} AND variant_type=#{type} AND profile=#{profile} AND status='READY' AND deleted_at IS NULL")
    boolean hasVariant(
            @Param("assetId") long assetId,
            @Param("type") String type,
            @Param("profile") String profile);

    @org.apache.ibatis.annotations.Select(
            """
            SELECT
              (SELECT COUNT(*) FROM diary_media WHERE asset_id=#{assetId}) AS diaries,
              (SELECT COUNT(*) FROM album_media am JOIN album al ON al.album_id=am.album_id
                 WHERE am.asset_id=#{assetId} AND al.deleted_at IS NULL) AS albums,
              (SELECT COUNT(*) FROM album WHERE cover_asset_id=#{assetId} AND deleted_at IS NULL) AS album_covers,
              (SELECT COUNT(*) FROM anniversary WHERE cover_asset_id=#{assetId} AND deleted_at IS NULL) AS anniversaries,
              (SELECT COUNT(*) FROM user_avatar WHERE asset_id=#{assetId}) AS avatars,
              (SELECT COUNT(*) FROM ai_album_candidate_media acm JOIN ai_album_candidate ac ON ac.candidate_id=acm.candidate_id
                 JOIN ai_album_proposal ap ON ap.proposal_id=ac.proposal_id
                 WHERE acm.asset_id=#{assetId} AND ap.status='PENDING') AS ai_proposals
            """)
    MediaRepository.ReferenceCounts references(long assetId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM favorite_media WHERE asset_id=#{assetId}")
    void removeFavorites(long assetId);

    @Update(
            "UPDATE media_asset SET caption=#{caption},taken_at=#{takenAt},access_scope=#{accessScope},"
                    + "library_visible=#{libraryVisible},updated_at=UTC_TIMESTAMP(6) WHERE space_id=#{spaceId} "
                    + "AND public_id=#{publicId} AND owner_id=#{accountId} AND deleted_at IS NULL")
    int updateMetadata(
            @Param("spaceId") long spaceId,
            @Param("publicId") byte[] publicId,
            @Param("accountId") long accountId,
            @Param("caption") String caption,
            @Param("takenAt") LocalDateTime takenAt,
            @Param("accessScope") String accessScope,
            @Param("libraryVisible") boolean libraryVisible);

    @Select(
            """
            SELECT a.account_id FROM account a JOIN space_member m ON m.account_id=a.account_id
            WHERE m.space_id=#{spaceId} AND m.status='ACTIVE' AND a.public_id=#{accountPublicId}
              AND a.status='ACTIVE'
            """)
    Long findActiveMemberAccountId(
            @Param("spaceId") long spaceId, @Param("accountPublicId") byte[] accountPublicId);

    @Update(
            """
            UPDATE media_asset SET owner_id=#{targetOwnerId},updated_at=UTC_TIMESTAMP(6)
            WHERE space_id=#{spaceId} AND public_id=#{assetPublicId}
              AND owner_id=#{currentOwnerId} AND status='READY' AND deleted_at IS NULL
            """)
    int transferOwnership(
            @Param("spaceId") long spaceId,
            @Param("assetPublicId") byte[] assetPublicId,
            @Param("currentOwnerId") long currentOwnerId,
            @Param("targetOwnerId") long targetOwnerId);

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

        public AssetInsert(
                byte[] publicId,
                long spaceId,
                long ownerId,
                String mediaType,
                String originalFilename,
                String caption,
                LocalDateTime takenAt,
                String accessScope,
                boolean libraryVisible,
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

        public Long getAssetId() {
            return assetId;
        }

        public void setAssetId(Long assetId) {
            this.assetId = assetId;
        }

        public byte[] getPublicId() {
            return publicId;
        }

        public long getSpaceId() {
            return spaceId;
        }

        public long getOwnerId() {
            return ownerId;
        }

        public String getMediaType() {
            return mediaType;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }

        public String getCaption() {
            return caption;
        }

        public LocalDateTime getTakenAt() {
            return takenAt;
        }

        public String getAccessScope() {
            return accessScope;
        }

        public boolean isLibraryVisible() {
            return libraryVisible;
        }

        public String getStatus() {
            return status;
        }
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

        public MediaRow() {}

        public long assetId() {
            return assetId;
        }

        public byte[] publicId() {
            return publicId;
        }

        public byte[] spacePublicId() {
            return spacePublicId;
        }

        public long ownerId() {
            return ownerId;
        }

        public String mediaType() {
            return mediaType;
        }

        public String originalFilename() {
            return originalFilename;
        }

        public String caption() {
            return caption;
        }

        public LocalDateTime takenAt() {
            return takenAt;
        }

        public String accessScope() {
            return accessScope;
        }

        public boolean libraryVisible() {
            return libraryVisible;
        }

        public String assetStatus() {
            return assetStatus;
        }

        public LocalDateTime assetCreatedAt() {
            return assetCreatedAt;
        }

        public LocalDateTime assetUpdatedAt() {
            return assetUpdatedAt;
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

        public String variantStatus() {
            return variantStatus;
        }

        public void setAssetId(long assetId) {
            this.assetId = assetId;
        }

        public void setPublicId(byte[] publicId) {
            this.publicId = publicId;
        }

        public void setSpacePublicId(byte[] spacePublicId) {
            this.spacePublicId = spacePublicId;
        }

        public void setOwnerId(long ownerId) {
            this.ownerId = ownerId;
        }

        public void setMediaType(String mediaType) {
            this.mediaType = mediaType;
        }

        public void setOriginalFilename(String originalFilename) {
            this.originalFilename = originalFilename;
        }

        public void setCaption(String caption) {
            this.caption = caption;
        }

        public void setTakenAt(LocalDateTime takenAt) {
            this.takenAt = takenAt;
        }

        public void setAccessScope(String accessScope) {
            this.accessScope = accessScope;
        }

        public void setLibraryVisible(boolean libraryVisible) {
            this.libraryVisible = libraryVisible;
        }

        public void setAssetStatus(String assetStatus) {
            this.assetStatus = assetStatus;
        }

        public void setAssetCreatedAt(LocalDateTime assetCreatedAt) {
            this.assetCreatedAt = assetCreatedAt;
        }

        public void setAssetUpdatedAt(LocalDateTime assetUpdatedAt) {
            this.assetUpdatedAt = assetUpdatedAt;
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

        public void setVariantStatus(String variantStatus) {
            this.variantStatus = variantStatus;
        }
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

        public VariantRow() {}

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

        public String status() {
            return status;
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

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
