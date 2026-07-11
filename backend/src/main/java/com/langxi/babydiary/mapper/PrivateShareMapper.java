package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.PrivateShare;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PrivateShareMapper {
    @Insert("INSERT INTO private_share(public_id,token_hash,space_id,diary_id,created_by,password_hash,expires_at,max_views) " +
            "VALUES(#{publicId},#{tokenHash},#{spaceId},#{diaryId},#{createdBy},#{passwordHash},#{expiresAt},#{maxViews})")
    @Options(useGeneratedKeys = true, keyProperty = "shareId")
    void insert(PrivateShare share);

    @Select("SELECT share_id shareId,public_id publicId,token_hash tokenHash,space_id spaceId,diary_id diaryId," +
            "created_by createdBy,password_hash passwordHash,expires_at expiresAt,max_views maxViews,view_count viewCount," +
            "revoked_at revokedAt,created_at createdAt FROM private_share WHERE token_hash=#{tokenHash} FOR UPDATE")
    PrivateShare findByTokenForUpdate(@Param("tokenHash") String tokenHash);

    @Select("SELECT share_id shareId,public_id publicId,token_hash tokenHash,space_id spaceId,diary_id diaryId," +
            "created_by createdBy,password_hash passwordHash,expires_at expiresAt,max_views maxViews,view_count viewCount," +
            "revoked_at revokedAt,created_at createdAt FROM private_share " +
            "WHERE diary_id=#{diaryId} AND created_by=#{userId} AND revoked_at IS NULL AND expires_at>NOW() " +
            "AND (max_views IS NULL OR view_count<max_views) ORDER BY created_at DESC")
    List<PrivateShare> findActiveByDiary(@Param("diaryId") Integer diaryId, @Param("userId") Integer userId);

    @Update("UPDATE private_share SET view_count=view_count+1 WHERE share_id=#{shareId} AND revoked_at IS NULL " +
            "AND expires_at>NOW() AND (max_views IS NULL OR view_count<max_views)")
    int incrementView(@Param("shareId") Long shareId);

    @Update("UPDATE private_share SET revoked_at=NOW() WHERE public_id=#{publicId} AND created_by=#{userId} AND revoked_at IS NULL")
    int revoke(@Param("publicId") String publicId, @Param("userId") Integer userId);
}
