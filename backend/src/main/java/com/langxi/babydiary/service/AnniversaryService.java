package com.langxi.babydiary.service;

import com.langxi.babydiary.common.CacheNames;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.entity.Anniversary;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AnniversaryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Date;
import java.util.List;

@Service
public class AnniversaryService {
    @Autowired
    private AnniversaryMapper anniversaryMapper;

    @Autowired
    private ImageStorageService imageStorageService;

    @Cacheable(cacheNames = CacheNames.ANNIVERSARIES, key = "#userId")
    public List<Anniversary> findByUserId(Integer userId) {
        return anniversaryMapper.findByUserId(userId);
    }

    public String uploadCover(Integer userId, MultipartFile coverFile) {
        if (coverFile == null || coverFile.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "封面图片不能为空");
        }
        try {
            return imageStorageService.storeImage(coverFile, coverPrefix(userId), true);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "封面图片上传失败");
        }
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.ANNIVERSARIES, key = "#userId")
    public Anniversary create(Integer userId, String title, String date, String description, String coverImagePath, Integer sort) {
        Anniversary anniversary = new Anniversary();
        anniversary.setUserId(userId);
        anniversary.setTitle(title);
        anniversary.setDate(Date.valueOf(date));
        anniversary.setDescription(description);
        anniversary.setCoverImagePath(normalizeCoverPath(userId, coverImagePath, null));
        anniversary.setSort(sort == null ? 0 : sort);
        anniversaryMapper.insertAnniversary(anniversary);
        return anniversaryMapper.findById(userId, anniversary.getAnniversaryId());
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.ANNIVERSARIES, key = "#userId")
    public Anniversary update(Integer userId, Integer anniversaryId, String title, String date, String description, String coverImagePath, Integer sort) {
        Anniversary existing = anniversaryMapper.findById(userId, anniversaryId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Anniversary anniversary = new Anniversary();
        anniversary.setUserId(userId);
        anniversary.setAnniversaryId(anniversaryId);
        anniversary.setTitle(title);
        anniversary.setDate(Date.valueOf(date));
        anniversary.setDescription(description);
        String normalizedCoverPath = normalizeCoverPath(userId, coverImagePath, existing.getCoverImagePath());
        anniversary.setCoverImagePath(normalizedCoverPath);
        anniversary.setSort(sort == null ? 0 : sort);
        anniversaryMapper.updateAnniversary(anniversary);
        if (!java.util.Objects.equals(existing.getCoverImagePath(), normalizedCoverPath)
                && imageStorageService.isOwnedPath(existing.getCoverImagePath(), coverPrefix(userId))) {
            imageStorageService.deleteAfterCommit(existing.getCoverImagePath());
        }
        return anniversaryMapper.findById(userId, anniversaryId);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.ANNIVERSARIES, key = "#userId")
    public void delete(Integer userId, Integer anniversaryId) {
        Anniversary existing = anniversaryMapper.findById(userId, anniversaryId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        anniversaryMapper.deleteAnniversary(userId, anniversaryId);
        if (imageStorageService.isOwnedPath(existing.getCoverImagePath(), coverPrefix(userId))) {
            imageStorageService.deleteAfterCommit(existing.getCoverImagePath());
        }
    }

    private String normalizeCoverPath(Integer userId, String coverImagePath, String existingCoverPath) {
        if (coverImagePath == null || coverImagePath.trim().isEmpty()) {
            return null;
        }
        String normalized = coverImagePath.trim();
        if (normalized.equals(existingCoverPath) || imageStorageService.isOwnedPath(normalized, coverPrefix(userId))) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "封面图片不属于当前用户");
    }

    private String coverPrefix(Integer userId) {
        return "anniversary_" + userId + "_";
    }
}
