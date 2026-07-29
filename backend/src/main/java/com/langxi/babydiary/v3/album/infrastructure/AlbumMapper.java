package com.langxi.babydiary.v3.album.infrastructure;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AlbumMapper {
    @Select("SELECT group_id,public_id,name,sort_order FROM album_group WHERE space_id=#{spaceId} ORDER BY sort_order,group_id")
    List<GroupRow> findGroups(long spaceId);

    @Select("""
            SELECT a.album_id,a.public_id,a.group_id,g.public_id AS group_public_id,a.type,a.name,a.description,
                   ca.public_id AS cover_public_id,COUNT(am.asset_id) AS media_count
            FROM album a LEFT JOIN album_media am ON am.space_id=a.space_id AND am.album_id=a.album_id
            LEFT JOIN album_group g ON g.space_id=a.space_id AND g.group_id=a.group_id
            LEFT JOIN media_asset ca ON ca.space_id=a.space_id AND ca.asset_id=a.cover_asset_id
            WHERE a.space_id=#{spaceId} AND a.deleted_at IS NULL
            GROUP BY a.album_id,a.public_id,a.group_id,g.public_id,a.type,a.name,a.description,ca.public_id
            ORDER BY a.sort_order,a.album_id
            """)
    List<AlbumRow> findAlbums(long spaceId);

    @Select("""
            SELECT a.album_id,a.public_id,a.group_id,g.public_id AS group_public_id,a.type,a.name,a.description,
                   ca.public_id AS cover_public_id,COUNT(am.asset_id) AS media_count
            FROM album a LEFT JOIN album_media am ON am.space_id=a.space_id AND am.album_id=a.album_id
            LEFT JOIN album_group g ON g.space_id=a.space_id AND g.group_id=a.group_id
            LEFT JOIN media_asset ca ON ca.space_id=a.space_id AND ca.asset_id=a.cover_asset_id
            WHERE a.space_id=#{spaceId} AND a.public_id=#{publicId} AND a.deleted_at IS NULL
            GROUP BY a.album_id,a.public_id,a.group_id,g.public_id,a.type,a.name,a.description,ca.public_id
            """)
    AlbumRow findAlbum(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId);

    @Select("""
            SELECT ma.public_id
            FROM album a JOIN album_media am ON am.space_id=a.space_id AND am.album_id=a.album_id
            JOIN media_asset ma ON ma.space_id=am.space_id AND ma.asset_id=am.asset_id
            WHERE a.space_id=#{spaceId} AND a.public_id=#{albumId} AND a.deleted_at IS NULL
              AND ma.deleted_at IS NULL AND ma.status='READY'
              AND (ma.owner_id=#{accountId} OR ma.access_scope='SPACE' OR ma.library_visible=true)
            ORDER BY am.position,am.asset_id
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<byte[]> findMediaPublicIds(@Param("spaceId") long spaceId, @Param("albumId") byte[] albumId,
                                    @Param("accountId") long accountId, @Param("offset") int offset,
                                    @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM album a JOIN album_media am ON am.space_id=a.space_id AND am.album_id=a.album_id " +
            "JOIN media_asset ma ON ma.space_id=am.space_id AND ma.asset_id=am.asset_id " +
            "WHERE a.space_id=#{spaceId} AND a.public_id=#{albumId} AND a.deleted_at IS NULL " +
            "AND ma.deleted_at IS NULL AND ma.status='READY' " +
            "AND (ma.owner_id=#{accountId} OR ma.access_scope='SPACE' OR ma.library_visible=true)")
    long countMedia(@Param("spaceId") long spaceId, @Param("albumId") byte[] albumId,
                    @Param("accountId") long accountId);

    @Insert("""
            INSERT INTO album_group(public_id,space_id,name,sort_order,created_by,created_at,updated_at)
            VALUES(#{publicId},#{spaceId},#{name},#{sortOrder},#{createdBy},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "groupId")
    void insertGroup(GroupInsert row);

    @Update("UPDATE album_group SET name=#{name},updated_at=UTC_TIMESTAMP(6) WHERE space_id=#{spaceId} AND public_id=#{publicId}")
    int updateGroup(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId, @Param("name") String name);

    @Delete("DELETE FROM album_group WHERE space_id=#{spaceId} AND public_id=#{publicId} AND NOT EXISTS " +
            "(SELECT 1 FROM album a WHERE a.space_id=#{spaceId} AND a.group_id=album_group.group_id AND a.deleted_at IS NULL)")
    int deleteGroup(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId);

    @Insert("""
            INSERT INTO album(public_id,space_id,group_id,created_by,name,description,type,cover_asset_id,sort_order,
              created_at,updated_at)
            VALUES(#{publicId},#{spaceId},#{groupId},#{createdBy},#{name},#{description},#{type},#{coverAssetId},
              #{sortOrder},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "albumId")
    void insertAlbum(AlbumInsert row);

    @Update("UPDATE album SET group_id=#{groupId},name=#{name},description=#{description},updated_at=UTC_TIMESTAMP(6) " +
            "WHERE space_id=#{spaceId} AND public_id=#{publicId} AND deleted_at IS NULL")
    int updateAlbum(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId,
                    @Param("groupId") Long groupId, @Param("name") String name,
                    @Param("description") String description);

    @Update("UPDATE album SET deleted_at=UTC_TIMESTAMP(6),updated_at=UTC_TIMESTAMP(6) " +
            "WHERE space_id=#{spaceId} AND public_id=#{publicId} AND deleted_at IS NULL")
    int softDeleteAlbum(@Param("spaceId") long spaceId, @Param("publicId") byte[] publicId);

    @Insert("INSERT INTO album_media(space_id,album_id,asset_id,position,created_at) VALUES(#{spaceId},#{albumId},#{assetId},#{position},UTC_TIMESTAMP(6))")
    void insertMedia(@Param("spaceId") long spaceId, @Param("albumId") long albumId,
                     @Param("assetId") long assetId, @Param("position") int position);

    @Delete("DELETE FROM album_media WHERE space_id=#{spaceId} AND album_id=#{albumId} AND asset_id=#{assetId}")
    void deleteMedia(@Param("spaceId") long spaceId, @Param("albumId") long albumId, @Param("assetId") long assetId);

    @Delete("DELETE FROM album_media WHERE space_id=#{spaceId} AND album_id=#{albumId}")
    void deleteAllMedia(@Param("spaceId") long spaceId, @Param("albumId") long albumId);

    @Update("UPDATE album SET cover_asset_id=#{assetId},updated_at=UTC_TIMESTAMP(6) WHERE space_id=#{spaceId} AND album_id=#{albumId}")
    void updateCover(@Param("spaceId") long spaceId, @Param("albumId") long albumId, @Param("assetId") Long assetId);

    @Insert("INSERT IGNORE INTO favorite_media(space_id,account_id,asset_id,created_at) VALUES(#{spaceId},#{accountId},#{assetId},UTC_TIMESTAMP(6))")
    void addFavorite(@Param("spaceId") long spaceId, @Param("accountId") long accountId, @Param("assetId") long assetId);

    @Delete("DELETE FROM favorite_media WHERE space_id=#{spaceId} AND account_id=#{accountId} AND asset_id=#{assetId}")
    void removeFavorite(@Param("spaceId") long spaceId, @Param("accountId") long accountId, @Param("assetId") long assetId);

    @Select("""
            SELECT a.public_id FROM favorite_media f JOIN media_asset a ON a.space_id=f.space_id AND a.asset_id=f.asset_id
            WHERE f.space_id=#{spaceId} AND f.account_id=#{accountId} AND a.media_type='IMAGE'
              AND a.deleted_at IS NULL AND a.status='READY'
            ORDER BY f.created_at DESC,f.asset_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<byte[]> findFavoritePublicIds(@Param("spaceId") long spaceId, @Param("accountId") long accountId,
                                       @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM favorite_media f JOIN media_asset a ON a.space_id=f.space_id AND a.asset_id=f.asset_id " +
            "WHERE f.space_id=#{spaceId} AND f.account_id=#{accountId} AND a.media_type='IMAGE' " +
            "AND a.deleted_at IS NULL AND a.status='READY'")
    long countFavoriteMedia(@Param("spaceId") long spaceId, @Param("accountId") long accountId);

    @Select("""
            SELECT a.public_id FROM media_asset a
            WHERE a.space_id=#{spaceId} AND a.media_type='IMAGE' AND a.library_visible=true
              AND a.deleted_at IS NULL AND a.status='READY'
              AND (a.owner_id=#{accountId} OR a.access_scope='SPACE' OR a.library_visible=true)
            ORDER BY a.created_at DESC,a.asset_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<byte[]> findLibraryPublicIds(@Param("spaceId") long spaceId, @Param("accountId") long accountId,
                                      @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM media_asset a WHERE a.space_id=#{spaceId} AND a.media_type='IMAGE' " +
            "AND a.library_visible=true AND a.deleted_at IS NULL AND a.status='READY' " +
            "AND (a.owner_id=#{accountId} OR a.access_scope='SPACE' OR a.library_visible=true)")
    long countLibraryImages(@Param("spaceId") long spaceId, @Param("accountId") long accountId);

    final class GroupInsert {
        private Long groupId;
        private final byte[] publicId;
        private final long spaceId;
        private final String name;
        private final int sortOrder;
        private final long createdBy;

        public GroupInsert(byte[] publicId, long spaceId, String name, int sortOrder, long createdBy) {
            this.publicId = publicId;
            this.spaceId = spaceId;
            this.name = name;
            this.sortOrder = sortOrder;
            this.createdBy = createdBy;
        }

        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public byte[] getPublicId() { return publicId; }
        public long getSpaceId() { return spaceId; }
        public String getName() { return name; }
        public int getSortOrder() { return sortOrder; }
        public long getCreatedBy() { return createdBy; }
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

        public AlbumInsert(byte[] publicId, long spaceId, Long groupId, long createdBy, String name,
                           String description, String type, Long coverAssetId, int sortOrder) {
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

        public Long getAlbumId() { return albumId; }
        public void setAlbumId(Long albumId) { this.albumId = albumId; }
        public byte[] getPublicId() { return publicId; }
        public long getSpaceId() { return spaceId; }
        public Long getGroupId() { return groupId; }
        public long getCreatedBy() { return createdBy; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getType() { return type; }
        public Long getCoverAssetId() { return coverAssetId; }
        public int getSortOrder() { return sortOrder; }
    }

    final class GroupRow {
        private long groupId;
        private byte[] publicId;
        private String name;
        private int sortOrder;

        public GroupRow() {
        }

        public long groupId() { return groupId; }
        public byte[] publicId() { return publicId; }
        public String name() { return name; }
        public int sortOrder() { return sortOrder; }

        public void setGroupId(long groupId) { this.groupId = groupId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setName(String name) { this.name = name; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
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
        private long mediaCount;

        public AlbumRow() {
        }

        public long albumId() { return albumId; }
        public byte[] publicId() { return publicId; }
        public Long groupId() { return groupId; }
        public byte[] groupPublicId() { return groupPublicId; }
        public String type() { return type; }
        public String name() { return name; }
        public String description() { return description; }
        public byte[] coverPublicId() { return coverPublicId; }
        public long mediaCount() { return mediaCount; }

        public void setAlbumId(long albumId) { this.albumId = albumId; }
        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }
        public void setGroupPublicId(byte[] groupPublicId) { this.groupPublicId = groupPublicId; }
        public void setType(String type) { this.type = type; }
        public void setName(String name) { this.name = name; }
        public void setDescription(String description) { this.description = description; }
        public void setCoverPublicId(byte[] coverPublicId) { this.coverPublicId = coverPublicId; }
        public void setMediaCount(long mediaCount) { this.mediaCount = mediaCount; }
    }
}
