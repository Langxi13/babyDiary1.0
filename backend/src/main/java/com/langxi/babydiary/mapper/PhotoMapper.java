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

    String findCoverImagePath(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("favoriteOnly") Boolean favoriteOnly);

    List<Integer> findPhotoYears(@Param("userId") Integer userId);

    List<Photo> findPhotosByIds(@Param("userId") Integer userId, @Param("imageIds") List<Integer> imageIds);

    Photo findPhotoById(@Param("userId") Integer userId, @Param("imageId") Integer imageId);

    void insertFavorite(@Param("userId") Integer userId, @Param("imageId") Integer imageId);

    void deleteFavorite(@Param("userId") Integer userId, @Param("imageId") Integer imageId);
}
