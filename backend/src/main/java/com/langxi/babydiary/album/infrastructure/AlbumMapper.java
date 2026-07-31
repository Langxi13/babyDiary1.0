package com.langxi.babydiary.album.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AlbumMapper {
    @Select(
            "SELECT group_id,public_id,name,sort_order FROM album_group WHERE space_id=#{spaceId} ORDER BY sort_order,group_id")
    List<GroupRow> findGroups(long spaceId);

    @Select(
            """
            SELECT a.album_id,a.public_id,a.group_id,g.public_id AS group_public_id,a.type,a.name,a.description,
                   ca.public_id AS cover_public_id,NULL AS cover_variant_type,
                   NULL AS cover_variant_profile,COUNT(ma.asset_id) AS media_count
            FROM album a LEFT JOIN album_media am ON am.space_id=a.space_id AND am.album_id=a.album_id
            LEFT JOIN media_asset ma ON ma.space_id=am.space_id AND ma.asset_id=am.asset_id
              AND ma.deleted_at IS NULL AND ma.status='READY'
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=ma.space_id AND lock_dm.asset_id=ma.asset_id AND lock_d.locked=1
              ))
            LEFT JOIN album_group g ON g.space_id=a.space_id AND g.group_id=a.group_id
            LEFT JOIN media_asset ca ON ca.space_id=a.space_id AND ca.asset_id=a.cover_asset_id
              AND ca.deleted_at IS NULL AND ca.status='READY'
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=ca.space_id AND lock_dm.asset_id=ca.asset_id AND lock_d.locked=1
              ))
            WHERE a.space_id=#{spaceId} AND a.deleted_at IS NULL
            GROUP BY a.album_id,a.public_id,a.group_id,g.public_id,a.type,a.name,a.description,ca.public_id
            ORDER BY a.sort_order,a.album_id
            """)
    List<AlbumRow> findAlbums(
            @Param("spaceId") long spaceId, @Param("includeProtected") boolean includeProtected);

    @Select(
            """
            SELECT a.album_id,a.public_id,a.group_id,g.public_id AS group_public_id,a.type,a.name,a.description,
                   ca.public_id AS cover_public_id,NULL AS cover_variant_type,
                   NULL AS cover_variant_profile,COUNT(ma.asset_id) AS media_count
            FROM album a LEFT JOIN album_media am ON am.space_id=a.space_id AND am.album_id=a.album_id
            LEFT JOIN media_asset ma ON ma.space_id=am.space_id AND ma.asset_id=am.asset_id
              AND ma.deleted_at IS NULL AND ma.status='READY'
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=ma.space_id AND lock_dm.asset_id=ma.asset_id AND lock_d.locked=1
              ))
            LEFT JOIN album_group g ON g.space_id=a.space_id AND g.group_id=a.group_id
            LEFT JOIN media_asset ca ON ca.space_id=a.space_id AND ca.asset_id=a.cover_asset_id
              AND ca.deleted_at IS NULL AND ca.status='READY'
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=ca.space_id AND lock_dm.asset_id=ca.asset_id AND lock_d.locked=1
              ))
            WHERE a.space_id=#{spaceId} AND a.public_id=#{publicId} AND a.deleted_at IS NULL
            GROUP BY a.album_id,a.public_id,a.group_id,g.public_id,a.type,a.name,a.description,ca.public_id
            """)
    AlbumRow findAlbum(
            @Param("spaceId") long spaceId,
            @Param("publicId") byte[] publicId,
            @Param("includeProtected") boolean includeProtected);

    @Select(
            """
            SELECT ma.public_id
            FROM album a JOIN album_media am ON am.space_id=a.space_id AND am.album_id=a.album_id
            JOIN media_asset ma ON ma.space_id=am.space_id AND ma.asset_id=am.asset_id
            WHERE a.space_id=#{spaceId} AND a.public_id=#{albumId} AND a.deleted_at IS NULL
              AND ma.deleted_at IS NULL AND ma.status='READY'
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=ma.space_id AND lock_dm.asset_id=ma.asset_id AND lock_d.locked=1
              ))
            ORDER BY am.position,am.asset_id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<byte[]> findMediaPublicIds(
            @Param("spaceId") long spaceId,
            @Param("albumId") byte[] albumId,
            @Param("includeProtected") boolean includeProtected,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select(
            """
            SELECT COUNT(*) FROM album a
            JOIN album_media am ON am.space_id=a.space_id AND am.album_id=a.album_id
            JOIN media_asset ma ON ma.space_id=am.space_id AND ma.asset_id=am.asset_id
            WHERE a.space_id=#{spaceId} AND a.public_id=#{albumId} AND a.deleted_at IS NULL
              AND ma.deleted_at IS NULL AND ma.status='READY'
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=ma.space_id AND lock_dm.asset_id=ma.asset_id AND lock_d.locked=1
              ))
            """)
    long countMedia(
            @Param("spaceId") long spaceId,
            @Param("albumId") byte[] albumId,
            @Param("includeProtected") boolean includeProtected);

    @Insert(
            """
            INSERT INTO album_group(public_id,space_id,name,sort_order,created_by,created_at,updated_at)
            VALUES(#{publicId},#{spaceId},#{name},#{sortOrder},#{createdBy},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "groupId")
    void insertGroup(GroupInsert row);

    @Update(
            "UPDATE album_group SET name=#{name},updated_at=UTC_TIMESTAMP(6) WHERE space_id=#{spaceId} AND public_id=#{publicId}")
    int updateGroup(
            @Param("spaceId") long spaceId,
            @Param("publicId") byte[] publicId,
            @Param("name") String name);

    @Delete(
            "DELETE FROM album_group WHERE space_id=#{spaceId} AND public_id=#{publicId} AND NOT EXISTS "
                    + "(SELECT 1 FROM album a WHERE a.space_id=#{spaceId} AND a.group_id=album_group.group_id AND a.deleted_at IS NULL)")
    int deleteGroup(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId);

    @Insert(
            """
            INSERT INTO album(public_id,space_id,group_id,created_by,name,description,type,cover_asset_id,sort_order,
              created_at,updated_at)
            VALUES(#{publicId},#{spaceId},#{groupId},#{createdBy},#{name},#{description},#{type},#{coverAssetId},
              #{sortOrder},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "albumId")
    void insertAlbum(AlbumInsert row);

    @Update(
            "UPDATE album SET group_id=#{groupId},name=#{name},description=#{description},updated_at=UTC_TIMESTAMP(6) "
                    + "WHERE space_id=#{spaceId} AND public_id=#{publicId} AND deleted_at IS NULL")
    int updateAlbum(
            @Param("spaceId") long spaceId,
            @Param("publicId") byte[] publicId,
            @Param("groupId") Long groupId,
            @Param("name") String name,
            @Param("description") String description);

    @Update(
            "UPDATE album SET deleted_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) "
                    + "WHERE space_id=#{spaceId} AND public_id=#{publicId} AND deleted_at IS NULL")
    int softDeleteAlbum(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId);

    @Insert(
            "INSERT INTO album_media(space_id,album_id,asset_id,position,created_at) VALUES(#{spaceId},#{albumId},#{assetId},#{position},UTC_TIMESTAMP(6))")
    void insertMedia(
            @Param("spaceId") long spaceId,
            @Param("albumId") long albumId,
            @Param("assetId") long assetId,
            @Param("position") int position);

    @Delete(
            "DELETE FROM album_media WHERE space_id=#{spaceId} AND album_id=#{albumId} AND asset_id=#{assetId}")
    void deleteMedia(
            @Param("spaceId") long spaceId,
            @Param("albumId") long albumId,
            @Param("assetId") long assetId);

    @Delete("DELETE FROM album_media WHERE space_id=#{spaceId} AND album_id=#{albumId}")
    void deleteAllMedia(@Param("spaceId") long spaceId, @Param("albumId") long albumId);

    @Update(
            "UPDATE album SET cover_asset_id=#{assetId},updated_at=UTC_TIMESTAMP(6) WHERE space_id=#{spaceId} AND album_id=#{albumId}")
    void updateCover(
            @Param("spaceId") long spaceId,
            @Param("albumId") long albumId,
            @Param("assetId") Long assetId);

    @Insert(
            "INSERT IGNORE INTO favorite_media(space_id,account_id,asset_id,created_at) VALUES(#{spaceId},#{accountId},#{assetId},UTC_TIMESTAMP(6))")
    void addFavorite(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("assetId") long assetId);

    @Delete(
            "DELETE FROM favorite_media WHERE space_id=#{spaceId} AND account_id=#{accountId} AND asset_id=#{assetId}")
    void removeFavorite(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("assetId") long assetId);

    @Select(
            """
            SELECT a.public_id FROM favorite_media f JOIN media_asset a ON a.space_id=f.space_id AND a.asset_id=f.asset_id
            WHERE f.space_id=#{spaceId} AND f.account_id=#{accountId} AND a.media_type='IMAGE'
              AND a.deleted_at IS NULL AND a.status='READY'
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=a.space_id AND lock_dm.asset_id=a.asset_id AND lock_d.locked=1
              ))
            ORDER BY f.created_at DESC,f.asset_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<byte[]> findFavoritePublicIds(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("includeProtected") boolean includeProtected,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select(
            """
            SELECT COUNT(*) FROM favorite_media f
            JOIN media_asset a ON a.space_id=f.space_id AND a.asset_id=f.asset_id
            WHERE f.space_id=#{spaceId} AND f.account_id=#{accountId} AND a.media_type='IMAGE'
              AND a.deleted_at IS NULL AND a.status='READY'
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=a.space_id AND lock_dm.asset_id=a.asset_id AND lock_d.locked=1
              ))
            """)
    long countFavoriteMedia(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("includeProtected") boolean includeProtected);

    @Select(
            """
            SELECT a.public_id FROM media_asset a
            WHERE a.space_id=#{spaceId} AND a.media_type='IMAGE' AND a.library_visible=true
              AND a.deleted_at IS NULL AND a.status='READY'
              AND (a.owner_id=#{accountId} OR a.access_scope='SPACE')
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=a.space_id AND lock_dm.asset_id=a.asset_id AND lock_d.locked=1
              ))
            ORDER BY a.created_at DESC,a.asset_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<byte[]> findLibraryPublicIds(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("includeProtected") boolean includeProtected,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select(
            """
            SELECT COUNT(*) FROM media_asset a
            WHERE a.space_id=#{spaceId} AND a.media_type='IMAGE' AND a.library_visible=true
              AND a.deleted_at IS NULL AND a.status='READY'
              AND (a.owner_id=#{accountId} OR a.access_scope='SPACE')
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=a.space_id AND lock_dm.asset_id=a.asset_id AND lock_d.locked=1
              ))
            """)
    long countLibraryImages(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("includeProtected") boolean includeProtected);

    @Select(
            """
            WITH ranked AS (
              SELECT YEAR(COALESCE(a.taken_at,a.created_at)) AS media_year,
                     a.public_id,
                     COUNT(*) OVER (PARTITION BY YEAR(COALESCE(a.taken_at,a.created_at))) AS media_count,
                     ROW_NUMBER() OVER (
                       PARTITION BY YEAR(COALESCE(a.taken_at,a.created_at))
                       ORDER BY COALESCE(a.taken_at,a.created_at) DESC,a.asset_id DESC
                     ) AS media_rank
              FROM media_asset a
              WHERE a.space_id=#{spaceId} AND a.media_type='IMAGE' AND a.library_visible=true
                AND a.deleted_at IS NULL AND a.status='READY'
                AND (a.owner_id=#{accountId} OR a.access_scope='SPACE')
                AND (#{includeProtected}=true OR NOT EXISTS (
                  SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                    ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                  WHERE lock_dm.space_id=a.space_id AND lock_dm.asset_id=a.asset_id AND lock_d.locked=1
                ))
            )
            SELECT media_year,media_count,public_id AS cover_public_id
            FROM ranked WHERE media_rank=1 ORDER BY media_year DESC
            """)
    List<YearRow> findLibraryYears(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("includeProtected") boolean includeProtected);

    @Select(
            """
            SELECT a.public_id FROM media_asset a
            WHERE a.space_id=#{spaceId} AND a.media_type='IMAGE' AND a.library_visible=true
              AND a.deleted_at IS NULL AND a.status='READY'
              AND (a.owner_id=#{accountId} OR a.access_scope='SPACE')
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=a.space_id AND lock_dm.asset_id=a.asset_id AND lock_d.locked=1
              ))
              AND ((a.taken_at>=#{start} AND a.taken_at<#{end})
                OR (a.taken_at IS NULL AND a.created_at>=#{start} AND a.created_at<#{end}))
            ORDER BY COALESCE(a.taken_at,a.created_at) DESC,a.asset_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<byte[]> findLibraryPublicIdsByRange(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("includeProtected") boolean includeProtected,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select(
            """
            SELECT COUNT(*) FROM media_asset a
            WHERE a.space_id=#{spaceId} AND a.media_type='IMAGE' AND a.library_visible=true
              AND a.deleted_at IS NULL AND a.status='READY'
              AND (a.owner_id=#{accountId} OR a.access_scope='SPACE')
              AND (#{includeProtected}=true OR NOT EXISTS (
                SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                  ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=a.space_id AND lock_dm.asset_id=a.asset_id AND lock_d.locked=1
              ))
              AND ((a.taken_at>=#{start} AND a.taken_at<#{end})
                OR (a.taken_at IS NULL AND a.created_at>=#{start} AND a.created_at<#{end}))
            """)
    long countLibraryImagesByRange(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("includeProtected") boolean includeProtected,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    final class YearRow {
        private int mediaYear;
        private long mediaCount;
        private byte[] coverPublicId;

        public YearRow() {}

        public int mediaYear() {
            return mediaYear;
        }

        public long mediaCount() {
            return mediaCount;
        }

        public byte[] coverPublicId() {
            return coverPublicId;
        }

        public void setMediaYear(int mediaYear) {
            this.mediaYear = mediaYear;
        }

        public void setMediaCount(long mediaCount) {
            this.mediaCount = mediaCount;
        }

        public void setCoverPublicId(byte[] coverPublicId) {
            this.coverPublicId = coverPublicId;
        }
    }

    final class GroupInsert {
        private Long groupId;
        private final byte[] publicId;
        private final long spaceId;
        private final String name;
        private final int sortOrder;
        private final long createdBy;

        public GroupInsert(
                byte[] publicId, long spaceId, String name, int sortOrder, long createdBy) {
            this.publicId = publicId;
            this.spaceId = spaceId;
            this.name = name;
            this.sortOrder = sortOrder;
            this.createdBy = createdBy;
        }

        public Long getGroupId() {
            return groupId;
        }

        public void setGroupId(Long groupId) {
            this.groupId = groupId;
        }

        public byte[] getPublicId() {
            return publicId;
        }

        public long getSpaceId() {
            return spaceId;
        }

        public String getName() {
            return name;
        }

        public int getSortOrder() {
            return sortOrder;
        }

        public long getCreatedBy() {
            return createdBy;
        }
    }

    final class AlbumInsert {
        private Long albumId;
        private final byte[] publicId;
        private final long spaceId;
        private final Long groupId;
        private final long createdBy;
        private final String name;
        private final String description;
        private final String type;
        private final Long coverAssetId;
        private final int sortOrder;

        public AlbumInsert(
                byte[] publicId,
                long spaceId,
                Long groupId,
                long createdBy,
                String name,
                String description,
                String type,
                Long coverAssetId,
                int sortOrder) {
            this.publicId = publicId;
            this.spaceId = spaceId;
            this.groupId = groupId;
            this.createdBy = createdBy;
            this.name = name;
            this.description = description;
            this.type = type;
            this.coverAssetId = coverAssetId;
            this.sortOrder = sortOrder;
        }

        public Long getAlbumId() {
            return albumId;
        }

        public void setAlbumId(Long albumId) {
            this.albumId = albumId;
        }

        public byte[] getPublicId() {
            return publicId;
        }

        public long getSpaceId() {
            return spaceId;
        }

        public Long getGroupId() {
            return groupId;
        }

        public long getCreatedBy() {
            return createdBy;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getType() {
            return type;
        }

        public Long getCoverAssetId() {
            return coverAssetId;
        }

        public int getSortOrder() {
            return sortOrder;
        }
    }

    final class GroupRow {
        private long groupId;
        private byte[] publicId;
        private String name;
        private int sortOrder;

        public GroupRow() {}

        public long groupId() {
            return groupId;
        }

        public byte[] publicId() {
            return publicId;
        }

        public String name() {
            return name;
        }

        public int sortOrder() {
            return sortOrder;
        }

        public void setGroupId(long groupId) {
            this.groupId = groupId;
        }

        public void setPublicId(byte[] publicId) {
            this.publicId = publicId;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setSortOrder(int sortOrder) {
            this.sortOrder = sortOrder;
        }
    }

    final class AlbumRow {
        private long albumId;
        private byte[] publicId;
        private Long groupId;
        private byte[] groupPublicId;
        private String type;
        private String name;
        private String description;
        private byte[] coverPublicId;
        private String coverVariantType;
        private String coverVariantProfile;
        private long mediaCount;

        public AlbumRow() {}

        public long albumId() {
            return albumId;
        }

        public byte[] publicId() {
            return publicId;
        }

        public Long groupId() {
            return groupId;
        }

        public byte[] groupPublicId() {
            return groupPublicId;
        }

        public String type() {
            return type;
        }

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

        public byte[] coverPublicId() {
            return coverPublicId;
        }

        public String coverVariantType() {
            return coverVariantType;
        }

        public String coverVariantProfile() {
            return coverVariantProfile;
        }

        public long mediaCount() {
            return mediaCount;
        }

        public void setAlbumId(long albumId) {
            this.albumId = albumId;
        }

        public void setPublicId(byte[] publicId) {
            this.publicId = publicId;
        }

        public void setGroupId(Long groupId) {
            this.groupId = groupId;
        }

        public void setGroupPublicId(byte[] groupPublicId) {
            this.groupPublicId = groupPublicId;
        }

        public void setType(String type) {
            this.type = type;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setCoverPublicId(byte[] coverPublicId) {
            this.coverPublicId = coverPublicId;
        }

        public void setCoverVariantType(String coverVariantType) {
            this.coverVariantType = coverVariantType;
        }

        public void setCoverVariantProfile(String coverVariantProfile) {
            this.coverVariantProfile = coverVariantProfile;
        }

        public void setMediaCount(long mediaCount) {
            this.mediaCount = mediaCount;
        }
    }
}
