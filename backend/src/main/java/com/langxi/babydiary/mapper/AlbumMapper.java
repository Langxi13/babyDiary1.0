package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.Album;
import com.langxi.babydiary.entity.AlbumGroup;
import com.langxi.babydiary.entity.Photo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AlbumMapper {
    AlbumGroup ensureAiGroup(@Param("userId") Integer userId);
    void insertGroup(AlbumGroup group);
    void updateGroup(AlbumGroup group);
    void deleteGroup(@Param("userId") Integer userId, @Param("groupId") Integer groupId);
    AlbumGroup findGroupById(@Param("userId") Integer userId, @Param("groupId") Integer groupId);
    List<AlbumGroup> findGroupsByUserId(@Param("userId") Integer userId);

    void insertAlbum(Album album);
    void updateAlbum(Album album);
    void deleteAlbum(@Param("userId") Integer userId, @Param("albumId") Integer albumId);
    Album findAlbumById(@Param("userId") Integer userId, @Param("albumId") Integer albumId);
    List<Album> findAlbumsByGroupIds(@Param("groupIds") List<Integer> groupIds);
    List<Album> findAiAlbumsByUserId(@Param("userId") Integer userId);

    void insertAlbumPhotos(@Param("albumId") Integer albumId, @Param("imageIds") List<Integer> imageIds);
    void deleteAlbumPhoto(@Param("albumId") Integer albumId, @Param("imageId") Integer imageId);
    List<Photo> findAlbumPhotos(@Param("userId") Integer userId, @Param("albumId") Integer albumId);
    List<Photo> findAlbumPhotoPage(
            @Param("userId") Integer userId,
            @Param("albumId") Integer albumId,
            @Param("limit") int limit,
            @Param("offset") long offset);
    int countAlbumPhotos(@Param("userId") Integer userId, @Param("albumId") Integer albumId);
}
