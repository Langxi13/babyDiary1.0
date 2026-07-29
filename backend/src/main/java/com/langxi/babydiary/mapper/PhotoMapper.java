package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.Photo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PhotoMapper {
    List<Photo> findPhotos(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("tagId") Integer tagId, @Param("moodKey") String moodKey, @Param("favoriteOnly") Boolean favoriteOnly);

    List<Photo> findPhotoPage(
            @Param("userId") Integer userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("tagId") Integer tagId,
            @Param("moodKey") String moodKey,
            @Param("favoriteOnly") Boolean favoriteOnly,
            @Param("limit") int limit,
            @Param("offset") long offset);

    int countPhotos(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("tagId") Integer tagId, @Param("moodKey") String moodKey, @Param("favoriteOnly") Boolean favoriteOnly);

    String findCoverAssetPublicId(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("favoriteOnly") Boolean favoriteOnly);

    List<Integer> findPhotoYears(@Param("userId") Integer userId);

    List<Photo> findPhotosByIds(@Param("userId") Integer userId, @Param("assetIds") List<?> assetIds);

    Photo findPhotoById(@Param("userId") Integer userId, @Param("assetId") Object assetId);

    void insertFavorite(@Param("userId") Integer userId, @Param("assetId") Object assetId);

    void deleteFavorite(@Param("userId") Integer userId, @Param("assetId") Object assetId);
}
