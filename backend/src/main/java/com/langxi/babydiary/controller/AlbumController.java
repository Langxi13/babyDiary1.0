package com.langxi.babydiary.controller;

import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.AlbumDTO;
import com.langxi.babydiary.dto.AlbumGroupDTO;
import com.langxi.babydiary.dto.AlbumGroupVO;
import com.langxi.babydiary.dto.AlbumPhotoDTO;
import com.langxi.babydiary.dto.AlbumVO;
import com.langxi.babydiary.dto.PhotoVO;
import com.langxi.babydiary.entity.Photo;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.AlbumService;
import com.langxi.babydiary.validation.DiaryRequestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    @Autowired
    private AlbumService albumService;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private DiaryRequestValidator requestValidator;

    @GetMapping("/groups")
    public Result<List<AlbumGroupVO>> listGroups() {
        return Result.success(albumService.listGroups(currentUser.getUserId()));
    }

    @GetMapping("/system/all/photos")
    public Result<List<PhotoVO>> allPhotos() {
        return photos(albumService.findSystemPhotos(currentUser.getUserId(), "all"));
    }

    @GetMapping("/system/all/photos/page")
    public Result<PageResult<PhotoVO>> allPhotoPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return photos(albumService.findSystemPhotoPage(currentUser.getUserId(), "all", page, size));
    }

    @GetMapping("/system/favorites/photos")
    public Result<List<PhotoVO>> favoritePhotos() {
        return photos(albumService.findSystemPhotos(currentUser.getUserId(), "favorites"));
    }

    @GetMapping("/system/favorites/photos/page")
    public Result<PageResult<PhotoVO>> favoritePhotoPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return photos(albumService.findSystemPhotoPage(currentUser.getUserId(), "favorites", page, size));
    }

    @GetMapping("/system/year/{year}/photos")
    public Result<List<PhotoVO>> yearPhotos(@PathVariable Integer year) {
        requestValidator.validateYearMonth(year, null);
        return photos(albumService.findSystemPhotos(currentUser.getUserId(), "year:" + year));
    }

    @GetMapping("/system/year/{year}/photos/page")
    public Result<PageResult<PhotoVO>> yearPhotoPage(
            @PathVariable Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        requestValidator.validateYearMonth(year, null);
        return photos(albumService.findSystemPhotoPage(currentUser.getUserId(), "year:" + year, page, size));
    }

    @GetMapping("/{albumId}/photos")
    public Result<List<PhotoVO>> albumPhotos(@PathVariable Integer albumId) {
        return photos(albumService.findAlbumPhotos(currentUser.getUserId(), albumId));
    }

    @GetMapping("/{albumId}/photos/page")
    public Result<PageResult<PhotoVO>> albumPhotoPage(
            @PathVariable Integer albumId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return photos(albumService.findAlbumPhotoPage(currentUser.getUserId(), albumId, page, size));
    }

    @PostMapping("/groups")
    public Result<AlbumGroupVO> createGroup(@Valid @RequestBody AlbumGroupDTO dto) {
        return Result.success("相册组已创建", AlbumGroupVO.fromEntity(albumService.createGroup(currentUser.getUserId(), dto), java.util.Collections.emptyList()));
    }

    @PutMapping("/groups/{groupId}")
    public Result<AlbumGroupVO> updateGroup(@PathVariable Integer groupId, @Valid @RequestBody AlbumGroupDTO dto) {
        return Result.success("相册组已更新", AlbumGroupVO.fromEntity(albumService.updateGroup(currentUser.getUserId(), groupId, dto), java.util.Collections.emptyList()));
    }

    @DeleteMapping("/groups/{groupId}")
    public Result<Void> deleteGroup(@PathVariable Integer groupId) {
        albumService.deleteGroup(currentUser.getUserId(), groupId);
        return Result.success("相册组已删除", null);
    }

    @PostMapping
    public Result<AlbumVO> createAlbum(@Valid @RequestBody AlbumDTO dto) {
        return Result.success("相册已创建", AlbumVO.fromEntity(albumService.createAlbum(currentUser.getUserId(), dto)));
    }

    @PutMapping("/{albumId}")
    public Result<AlbumVO> updateAlbum(@PathVariable Integer albumId, @Valid @RequestBody AlbumDTO dto) {
        return Result.success("相册已更新", AlbumVO.fromEntity(albumService.updateAlbum(currentUser.getUserId(), albumId, dto)));
    }

    @DeleteMapping("/{albumId}")
    public Result<Void> deleteAlbum(@PathVariable Integer albumId) {
        albumService.deleteAlbum(currentUser.getUserId(), albumId);
        return Result.success("相册已删除", null);
    }

    @PostMapping("/{albumId}/photos")
    public Result<Void> addPhotos(@PathVariable Integer albumId, @Valid @RequestBody AlbumPhotoDTO dto) {
        albumService.addPhotos(currentUser.getUserId(), albumId, dto.getImageIds());
        return Result.success("照片已加入相册", null);
    }

    @DeleteMapping("/{albumId}/photos/{imageId}")
    public Result<Void> removePhoto(@PathVariable Integer albumId, @PathVariable Integer imageId) {
        albumService.removePhoto(currentUser.getUserId(), albumId, imageId);
        return Result.success("照片已移出相册", null);
    }

    private Result<List<PhotoVO>> photos(List<Photo> photos) {
        return Result.success(photos.stream().map(PhotoVO::fromEntity).collect(Collectors.toList()));
    }

    private Result<PageResult<PhotoVO>> photos(PageResult<Photo> page) {
        return Result.success(page.map(PhotoVO::fromEntity));
    }
}
