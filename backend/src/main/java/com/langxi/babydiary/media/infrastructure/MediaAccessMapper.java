package com.langxi.babydiary.media.infrastructure;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MediaAccessMapper {
    String PROTECTED_ASSET =
            "EXISTS (SELECT 1 FROM diary_media lock_dm JOIN diary lock_d "
                    + "ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id "
                    + "WHERE lock_dm.space_id=a.space_id AND lock_dm.asset_id=a.asset_id AND lock_d.locked=1)";
    String DIRECT_SQL =
            "SELECT COUNT(*)>0 AS can_access,COALESCE(MAX("
                    + PROTECTED_ASSET
                    + "),0) AS protected_content FROM diary_space s JOIN media_asset a ON a.space_id=s.space_id "
                    + "JOIN space_member m ON m.space_id=s.space_id AND m.account_id=#{accountId} AND m.status='ACTIVE' "
                    + "WHERE s.public_id=#{spaceId} AND a.public_id=#{assetId} AND a.deleted_at IS NULL AND a.status='READY' "
                    + "AND (a.owner_id=#{accountId} OR a.access_scope='SPACE')";
    String DIARY_SQL =
            "SELECT COUNT(*)>0 AS can_access,COALESCE(MAX(d.locked=1 OR "
                    + PROTECTED_ASSET
                    + "),0) AS protected_content FROM diary_space s JOIN diary d ON d.space_id=s.space_id "
                    + "JOIN diary_media dm ON dm.space_id=d.space_id AND dm.diary_id=d.diary_id "
                    + "JOIN media_asset a ON a.space_id=dm.space_id AND a.asset_id=dm.asset_id "
                    + "JOIN space_member m ON m.space_id=s.space_id AND m.account_id=#{accountId} AND m.status='ACTIVE' "
                    + "WHERE s.public_id=#{spaceId} AND a.public_id=#{assetId} AND d.public_id=#{contextId} "
                    + "AND d.deleted_at IS NULL AND (d.visibility='SHARED' OR d.author_id=#{accountId}) "
                    + "AND a.deleted_at IS NULL AND a.status='READY'";
    String ALBUM_SQL =
            "SELECT COUNT(*)>0 AS can_access,COALESCE(MAX("
                    + PROTECTED_ASSET
                    + "),0) AS protected_content FROM diary_space s JOIN album al ON al.space_id=s.space_id "
                    + "JOIN album_media am ON am.space_id=al.space_id AND am.album_id=al.album_id "
                    + "JOIN media_asset a ON a.space_id=am.space_id AND a.asset_id=am.asset_id "
                    + "JOIN space_member m ON m.space_id=s.space_id AND m.account_id=#{accountId} AND m.status='ACTIVE' "
                    + "WHERE s.public_id=#{spaceId} AND a.public_id=#{assetId} AND al.public_id=#{contextId} "
                    + "AND al.deleted_at IS NULL AND a.deleted_at IS NULL AND a.status='READY'";
    String ANNIVERSARY_SQL =
            "SELECT COUNT(*)>0 AS can_access,COALESCE(MAX("
                    + PROTECTED_ASSET
                    + "),0) AS protected_content FROM diary_space s JOIN anniversary an ON an.space_id=s.space_id "
                    + "JOIN media_asset a ON a.space_id=an.space_id AND a.asset_id=an.cover_asset_id "
                    + "JOIN space_member m ON m.space_id=s.space_id AND m.account_id=#{accountId} AND m.status='ACTIVE' "
                    + "WHERE s.public_id=#{spaceId} AND a.public_id=#{assetId} AND an.public_id=#{contextId} "
                    + "AND an.deleted_at IS NULL AND a.deleted_at IS NULL AND a.status='READY'";
    String AVATAR_SQL =
            "SELECT COUNT(*)>0 AS can_access,COALESCE(MAX("
                    + PROTECTED_ASSET
                    + "),0) AS protected_content FROM account target JOIN user_avatar ua ON ua.account_id=target.account_id "
                    + "JOIN diary_space s ON s.space_id=ua.space_id "
                    + "JOIN media_asset a ON a.space_id=ua.space_id AND a.asset_id=ua.asset_id "
                    + "WHERE s.public_id=#{spaceId} AND a.public_id=#{assetId} AND target.public_id=#{contextId} "
                    + "AND a.deleted_at IS NULL AND a.status='READY'";
    String SHARE_SQL =
            "SELECT COUNT(*)>0 AS can_access,COALESCE(MAX(d.locked=1 OR "
                    + PROTECTED_ASSET
                    + "),0) AS protected_content FROM private_share ps JOIN diary_space s ON s.space_id=ps.space_id "
                    + "JOIN diary d ON d.space_id=ps.space_id AND d.diary_id=ps.diary_id "
                    + "JOIN diary_media dm ON dm.space_id=d.space_id AND dm.diary_id=d.diary_id "
                    + "JOIN media_asset a ON a.space_id=dm.space_id AND a.asset_id=dm.asset_id "
                    + "WHERE s.public_id=#{spaceId} AND a.public_id=#{assetId} AND ps.public_id=#{contextId} "
                    + "AND ps.revoked_at IS NULL AND ps.expires_at>UTC_TIMESTAMP(6) "
                    + "AND d.deleted_at IS NULL AND a.deleted_at IS NULL AND a.status='READY'";
    String PROTECTED_SQL =
            "SELECT "
                    + PROTECTED_ASSET
                    + " FROM diary_space s JOIN media_asset a ON a.space_id=s.space_id "
                    + "WHERE s.public_id=#{spaceId} AND a.public_id=#{assetId}";

    @Select(DIRECT_SQL)
    AccessRow direct(
            @Param("spaceId") byte[] spaceId,
            @Param("assetId") byte[] assetId,
            @Param("accountId") long accountId);

    @Select(DIARY_SQL)
    AccessRow diary(
            @Param("spaceId") byte[] spaceId,
            @Param("assetId") byte[] assetId,
            @Param("contextId") byte[] contextId,
            @Param("accountId") long accountId);

    @Select(ALBUM_SQL)
    AccessRow album(
            @Param("spaceId") byte[] spaceId,
            @Param("assetId") byte[] assetId,
            @Param("contextId") byte[] contextId,
            @Param("accountId") long accountId);

    @Select(ANNIVERSARY_SQL)
    AccessRow anniversary(
            @Param("spaceId") byte[] spaceId,
            @Param("assetId") byte[] assetId,
            @Param("contextId") byte[] contextId,
            @Param("accountId") long accountId);

    @Select(AVATAR_SQL)
    AccessRow avatar(
            @Param("spaceId") byte[] spaceId,
            @Param("assetId") byte[] assetId,
            @Param("contextId") byte[] contextId);

    @Select(SHARE_SQL)
    AccessRow share(
            @Param("spaceId") byte[] spaceId,
            @Param("assetId") byte[] assetId,
            @Param("contextId") byte[] contextId);

    @Select(PROTECTED_SQL)
    Boolean protectedAsset(@Param("spaceId") byte[] spaceId, @Param("assetId") byte[] assetId);

    @Select(
            """
            <script>
            SELECT a.public_id FROM diary_space s JOIN media_asset a ON a.space_id=s.space_id
            WHERE s.public_id=#{spaceId} AND a.public_id IN
              <foreach collection='assetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
              AND EXISTS (SELECT 1 FROM diary_media lock_dm JOIN diary lock_d
                ON lock_d.space_id=lock_dm.space_id AND lock_d.diary_id=lock_dm.diary_id
                WHERE lock_dm.space_id=a.space_id AND lock_dm.asset_id=a.asset_id AND lock_d.locked=1)
            </script>
            """)
    List<byte[]> protectedAssets(
            @Param("spaceId") byte[] spaceId, @Param("assetIds") List<byte[]> assetIds);

    record AccessRow(boolean canAccess, boolean protectedContent) {}
}
