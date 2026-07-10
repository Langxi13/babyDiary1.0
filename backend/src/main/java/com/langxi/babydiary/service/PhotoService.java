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
import java.util.List;

@Service
public class PhotoService {
    @Autowired
    private PhotoMapper photoMapper;

    @Cacheable(cacheNames = CacheNames.PHOTOS, key = "#userId + ':' + #startDate + ':' + #endDate + ':' + #tagId + ':' + #moodKey + ':' + #favoriteOnly")
    public List<Photo> findPhotos(Integer userId, String startDate, String endDate, Integer tagId, String moodKey, Boolean favoriteOnly) {
        return photoMapper.findPhotos(userId, startDate, endDate, tagId, moodKey, favoriteOnly);
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
        return new PageResult<>(content, normalizedPage, normalizedSize, total);
    }

    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public Photo favorite(Integer userId, Integer imageId) {
        Photo photo = photoMapper.findPhotoById(userId, imageId);
        if (photo == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        photoMapper.insertFavorite(userId, imageId);
        return photoMapper.findPhotoById(userId, imageId);
    }

    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public void unfavorite(Integer userId, Integer imageId) {
        photoMapper.deleteFavorite(userId, imageId);
    }
}
