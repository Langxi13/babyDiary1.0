package com.langxi.babydiary.service;

import com.langxi.babydiary.common.CacheNames;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Pagination;
import com.langxi.babydiary.entity.Photo;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.PhotoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PhotoService {
    @Autowired
    private PhotoMapper photoMapper;

    @Autowired
    private MediaService mediaService;

    @Cacheable(cacheNames = CacheNames.PHOTOS, key = "#userId + ':' + #startDate + ':' + #endDate + ':' + #tagId + ':' + #moodKey + ':' + #favoriteOnly")
    public List<Photo> findPhotos(Integer userId, String startDate, String endDate, Integer tagId, String moodKey, Boolean favoriteOnly) {
        return enrichMedia(photoMapper.findPhotos(userId, startDate, endDate, tagId, moodKey, favoriteOnly));
    }

    @Cacheable(cacheNames = CacheNames.PHOTOS, key = "'page:' + #userId + ':' + #startDate + ':' + #endDate + ':' + #tagId + ':' + #moodKey + ':' + #favoriteOnly + ':' + #page + ':' + #size")
    public PageResult<Photo> findPhotoPage(
            Integer userId,
            String startDate,
            String endDate,
            Integer tagId,
            String moodKey,
            Boolean favoriteOnly,
            int page,
            int size) {
        int normalizedPage = Pagination.normalizePage(page);
        int normalizedSize = Pagination.normalizeSize(size);
        long total = photoMapper.countPhotos(userId, startDate, endDate, tagId, moodKey, favoriteOnly);
        List<Photo> content = total == 0
                ? Collections.emptyList()
                : photoMapper.findPhotoPage(
                        userId,
                        startDate,
                        endDate,
                        tagId,
                        moodKey,
                        favoriteOnly,
                        normalizedSize,
                        Pagination.offset(normalizedPage, normalizedSize));
        return new PageResult<>(enrichMedia(content), normalizedPage, normalizedSize, total);
    }

    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public Photo favorite(Integer userId, String assetId) {
        Photo photo = photoMapper.findPhotoById(userId, assetId);
        if (photo == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        photoMapper.insertFavorite(userId, assetId);
        return enrichOne(photoMapper.findPhotoById(userId, assetId));
    }

    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public void unfavorite(Integer userId, String assetId) {
        photoMapper.deleteFavorite(userId, assetId);
    }

    List<Photo> enrichMedia(List<Photo> photos) {
        if (photos == null || photos.isEmpty() || mediaService == null) return photos;
        List<String> ids = photos.stream().map(Photo::getAssetId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return photos;
        Map<String, com.langxi.babydiary.entity.MediaAsset> assets = mediaService.findByPublicIds(ids).stream()
                .collect(Collectors.toMap(com.langxi.babydiary.entity.MediaAsset::getPublicId, value -> value,
                        (left, right) -> left, HashMap::new));
        photos.forEach(photo -> {
            var asset = assets.get(photo.getAssetId());
            if (asset != null) photo.setMedia(mediaService.toVO(asset));
        });
        return photos;
    }

    private Photo enrichOne(Photo photo) {
        return photo == null ? null : enrichMedia(Collections.singletonList(photo)).get(0);
    }
}
