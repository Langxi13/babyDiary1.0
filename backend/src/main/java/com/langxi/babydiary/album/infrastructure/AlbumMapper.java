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
            """
            WITH library AS (
              SELECT a.asset_id,a.public_id,YEAR(COALESCE(a.taken_at,a.created_at)) AS media_year,
                     COALESCE(a.taken_at,a.created_at) AS media_time
              FROM media_asset a
              WHERE a.space_id=#{spaceId} AND a.media_type='IMAGE' AND a.library_visible=true
                AND a.deleted_at IS NULL AND a.status='READY'
                AND (a.owner_id=#{accountId} OR a.access_scope='SPACE')
                AND (#{includeProtected}=true OR NOT EXISTS (
                  SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                    ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                  WHERE lock_dm.space_id=a.space_id AND lock_dm.asset_id=a.asset_id AND lock_d.locked=1
                ))
            ), library_ranked AS (
              SELECT library.*,
                     ROW_NUMBER() OVER(ORDER BY media_time DESC,asset_id DESC) AS overall_rank,
                     ROW_NUMBER() OVER(PARTITION BY media_year ORDER BY media_time DESC,asset_id DESC) AS year_rank,
                     COUNT(*) OVER() AS overall_count,
                     COUNT(*) OVER(PARTITION BY media_year) AS year_count
              FROM library
            ), favorites AS (
              SELECT a.asset_id,a.public_id,f.created_at
              FROM favorite_media f JOIN media_asset a ON a.space_id=f.space_id AND a.asset_id=f.asset_id
              WHERE f.space_id=#{spaceId} AND f.account_id=#{accountId} AND a.media_type='IMAGE'
                AND a.deleted_at IS NULL AND a.status='READY'
                AND (#{includeProtected}=true OR NOT EXISTS (
                  SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                    ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                  WHERE lock_dm.space_id=a.space_id AND lock_dm.asset_id=a.asset_id AND lock_d.locked=1
                ))
            ), favorite_ranked AS (
              SELECT favorites.*,ROW_NUMBER() OVER(ORDER BY created_at DESC,asset_id DESC) AS media_rank,
                     COUNT(*) OVER() AS media_count
              FROM favorites
            )
            SELECT 'all' AS system_key,COALESCE(MAX(overall_count),0) AS media_count,
                   MAX(CASE WHEN overall_rank=1 THEN public_id END) AS cover_public_id,0 AS sort_order
            FROM library_ranked
            UNION ALL
            SELECT 'favorites',COALESCE(MAX(media_count),0),
                   MAX(CASE WHEN media_rank=1 THEN public_id END),1
            FROM favorite_ranked
            UNION ALL
            SELECT CONCAT('year:',media_year),MAX(year_count),
                   MAX(CASE WHEN year_rank=1 THEN public_id END),10000-media_year
            FROM library_ranked GROUP BY media_year
            ORDER BY sort_order
            """)
    List<SystemCatalogRow> findSystemCatalog(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("includeProtected") boolean includeProtected);

    @Select(
            """
            WITH accessible_media AS (
              SELECT a.album_id,am.asset_id,ma.public_id,
                     ROW_NUMBER() OVER(PARTITION BY a.album_id ORDER BY am.position,am.asset_id) AS media_rank,
                     COUNT(*) OVER(PARTITION BY a.album_id) AS media_count
              FROM album a JOIN album_media am ON am.space_id=a.space_id AND am.album_id=a.album_id
              JOIN media_asset ma ON ma.space_id=am.space_id AND ma.asset_id=am.asset_id
              WHERE a.space_id=#{spaceId} AND a.deleted_at IS NULL
                AND ma.deleted_at IS NULL AND ma.status='READY'
                AND (#{includeProtected}=true OR NOT EXISTS (
                  SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                    ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                  WHERE lock_dm.space_id=ma.space_id AND lock_dm.asset_id=ma.asset_id AND lock_d.locked=1
                ))
            ), album_rows AS (
              SELECT a.album_id,a.public_id,a.group_id,a.type,a.name,a.description,a.sort_order,
                     COALESCE(explicit_cover.public_id,fallback_cover.public_id) AS cover_public_id,
                     COALESCE(fallback_cover.media_count,0) AS media_count
              FROM album a
              LEFT JOIN accessible_media explicit_cover
                ON explicit_cover.album_id=a.album_id AND explicit_cover.asset_id=a.cover_asset_id
              LEFT JOIN accessible_media fallback_cover
                ON fallback_cover.album_id=a.album_id AND fallback_cover.media_rank=1
              WHERE a.space_id=#{spaceId} AND a.deleted_at IS NULL
            )
            SELECT g.group_id,g.public_id AS group_public_id,g.name AS group_name,g.sort_order AS group_sort_order,
                   a.album_id,a.public_id AS album_public_id,a.type AS album_type,a.name AS album_name,
                   a.description AS album_description,a.cover_public_id,a.media_count,
                   COALESCE(a.sort_order,0) AS album_sort_order
            FROM album_group g LEFT JOIN album_rows a ON a.group_id=g.group_id
            WHERE g.space_id=#{spaceId}
            UNION ALL
            SELECT NULL,NULL,NULL,2147483647,a.album_id,a.public_id,a.type,a.name,a.description,
                   a.cover_public_id,a.media_count,a.sort_order
            FROM album_rows a WHERE a.group_id IS NULL
            ORDER BY group_sort_order,group_id,album_sort_order,album_id
            """)
    List<CustomCatalogRow> findCustomCatalog(
            @Param("spaceId") long spaceId, @Param("includeProtected") boolean includeProtected);

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
            WITH ranked AS (
              SELECT a.public_id AS album_public_id,ma.public_id AS asset_public_id,
                     ROW_NUMBER() OVER (
                       PARTITION BY a.album_id ORDER BY am.position,am.asset_id
                     ) AS media_rank
              FROM album a JOIN album_media am
                ON am.space_id=a.space_id AND am.album_id=a.album_id
              JOIN media_asset ma ON ma.space_id=am.space_id AND ma.asset_id=am.asset_id
              WHERE a.space_id=#{spaceId} AND a.deleted_at IS NULL
                AND ma.deleted_at IS NULL AND ma.status='READY'
                AND (#{includeProtected}=true OR NOT EXISTS (
                  SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                    ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                  WHERE lock_dm.space_id=ma.space_id AND lock_dm.asset_id=ma.asset_id
                    AND lock_d.locked=1
                ))
            )
            SELECT album_public_id,asset_public_id FROM ranked WHERE media_rank=1
            """)
    List<AlbumCoverRow> findFallbackCovers(
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

    final class SystemCatalogRow {
        private String systemKey;
        private long mediaCount;
        private byte[] coverPublicId;

        public String systemKey() {
            return systemKey;
        }

        public long mediaCount() {
            return mediaCount;
        }

        public byte[] coverPublicId() {
            return coverPublicId;
        }

        public void setSystemKey(String value) {
            systemKey = value;
        }

        public void setMediaCount(long value) {
            mediaCount = value;
        }

        public void setCoverPublicId(byte[] value) {
            coverPublicId = value;
        }
    }

    final class CustomCatalogRow {
        private Long groupId;
        private byte[] groupPublicId;
        private String groupName;
        private Integer groupSortOrder;
        private Long albumId;
        private byte[] albumPublicId;
        private String albumType;
        private String albumName;
        private String albumDescription;
        private byte[] coverPublicId;
        private long mediaCount;

        public Long groupId() {
            return groupId;
        }

        public byte[] groupPublicId() {
            return groupPublicId;
        }

        public String groupName() {
            return groupName;
        }

        public Integer groupSortOrder() {
            return groupSortOrder;
        }

        public Long albumId() {
            return albumId;
        }

        public byte[] albumPublicId() {
            return albumPublicId;
        }

        public String albumType() {
            return albumType;
        }

        public String albumName() {
            return albumName;
        }

        public String albumDescription() {
            return albumDescription;
        }

        public byte[] coverPublicId() {
            return coverPublicId;
        }

        public long mediaCount() {
            return mediaCount;
        }

        public void setGroupId(Long value) {
            groupId = value;
        }

        public void setGroupPublicId(byte[] value) {
            groupPublicId = value;
        }

        public void setGroupName(String value) {
            groupName = value;
        }

        public void setGroupSortOrder(Integer value) {
            groupSortOrder = value;
        }

        public void setAlbumId(Long value) {
            albumId = value;
        }

        public void setAlbumPublicId(byte[] value) {
            albumPublicId = value;
        }

        public void setAlbumType(String value) {
            albumType = value;
        }

        public void setAlbumName(String value) {
            albumName = value;
        }

        public void setAlbumDescription(String value) {
            albumDescription = value;
        }

        public void setCoverPublicId(byte[] value) {
            coverPublicId = value;
        }

        public void setMediaCount(long value) {
            mediaCount = value;
        }
    }

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

    final class AlbumCoverRow {
        private byte[] albumPublicId;
        private byte[] assetPublicId;

        public AlbumCoverRow() {}

        public byte[] albumPublicId() {
            return albumPublicId;
        }

        public byte[] assetPublicId() {
            return assetPublicId;
        }

        public void setAlbumPublicId(byte[] albumPublicId) {
            this.albumPublicId = albumPublicId;
        }

        public void setAssetPublicId(byte[] assetPublicId) {
            this.assetPublicId = assetPublicId;
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
