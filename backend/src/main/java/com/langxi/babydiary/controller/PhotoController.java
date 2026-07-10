package com.langxi.babydiary.controller;

import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.PhotoVO;
import com.langxi.babydiary.entity.Photo;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.PhotoService;
import com.langxi.babydiary.validation.DiaryRequestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {
    @Autowired
    private PhotoService photoService;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private DiaryRequestValidator requestValidator;

    @GetMapping
    public Result<List<PhotoVO>> listPhotos(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer tagId,
            @RequestParam(required = false) String moodKey,
            @RequestParam(required = false) Boolean favoriteOnly) {
        DiaryRequestValidator.DateRange dateRange = requestValidator.optionalDateRange(startDate, endDate);
        List<PhotoVO> photos = photoService.findPhotos(
                        currentUser.getUserId(),
                        dateRange.startDate(),
                        dateRange.endDate(),
                        tagId,
                        requestValidator.normalizeMoodKey(moodKey),
                        favoriteOnly)
                .stream()
                .map(PhotoVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(photos);
    }

    @GetMapping("/page")
    public Result<PageResult<PhotoVO>> listPhotoPage(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Integer tagId,
            @RequestParam(required = false) String moodKey,
            @RequestParam(required = false) Boolean favoriteOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        DiaryRequestValidator.DateRange dateRange = requestValidator.optionalDateRange(startDate, endDate);
        PageResult<Photo> photos = photoService.findPhotoPage(
                currentUser.getUserId(),
                dateRange.startDate(),
                dateRange.endDate(),
                tagId,
                requestValidator.normalizeMoodKey(moodKey),
                favoriteOnly,
                page,
                size);
        return Result.success(photos.map(PhotoVO::fromEntity));
    }

    @PostMapping("/{imageId}/favorite")
    public Result<PhotoVO> favoritePhoto(@PathVariable Integer imageId) {
        return Result.success("已收藏", PhotoVO.fromEntity(photoService.favorite(currentUser.getUserId(), imageId)));
    }

    @DeleteMapping("/{imageId}/favorite")
    public Result<Void> unfavoritePhoto(@PathVariable Integer imageId) {
        photoService.unfavorite(currentUser.getUserId(), imageId);
        return Result.success("已取消收藏", null);
    }
}
