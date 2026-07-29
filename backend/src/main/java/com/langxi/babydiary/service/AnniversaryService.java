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
    private MediaService mediaService;

    @Autowired
    private SpaceService spaceService;

    @Cacheable(cacheNames = CacheNames.ANNIVERSARIES, key = "#userId")
    public List<Anniversary> findByUserId(Integer userId) {
        return anniversaryMapper.findByUserId(userId).stream().peek(this::enrichCover).toList();
    }

    public String uploadCover(Integer userId, MultipartFile coverFile) {
        if (coverFile == null || coverFile.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "封面图片不能为空");
        }
        try {
            return mediaService.upload(spaceService.requirePersonalSpace(userId).getPublicId(), userId, coverFile,
                    null, "纪念日封面", null, null, null, null, null).getAssetId();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "封面图片上传失败");
        }
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.ANNIVERSARIES, key = "#userId")
    public Anniversary create(Integer userId, String title, String date, String description, String coverAssetId, Integer sort) {
        Anniversary anniversary = new Anniversary();
        anniversary.setUserId(userId);
        anniversary.setTitle(title);
        anniversary.setDate(Date.valueOf(date));
        anniversary.setDescription(description);
        anniversary.setCoverAssetId(resolveCoverAssetId(userId, coverAssetId));
        anniversary.setSort(sort == null ? 0 : sort);
        anniversaryMapper.insertAnniversary(anniversary);
        Anniversary created = anniversaryMapper.findById(userId, anniversary.getAnniversaryId());
        enrichCover(created);
        return created;
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.ANNIVERSARIES, key = "#userId")
    public Anniversary update(Integer userId, Integer anniversaryId, String title, String date, String description, String coverAssetId, Integer sort) {
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
        anniversary.setCoverAssetId(resolveCoverAssetId(userId, coverAssetId));
        anniversary.setSort(sort == null ? 0 : sort);
        anniversaryMapper.updateAnniversary(anniversary);
        Anniversary updated = anniversaryMapper.findById(userId, anniversaryId);
        enrichCover(updated);
        return updated;
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.ANNIVERSARIES, key = "#userId")
    public void delete(Integer userId, Integer anniversaryId) {
        Anniversary existing = anniversaryMapper.findById(userId, anniversaryId);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        anniversaryMapper.deleteAnniversary(userId, anniversaryId);
    }

    private Long resolveCoverAssetId(Integer userId, String coverAssetId) {
        if (coverAssetId == null || coverAssetId.trim().isEmpty()) {
            return null;
        }
        return mediaService.requireOwnedAsset(spaceService.requirePersonalSpace(userId).getPublicId(), coverAssetId.trim(), userId).getAssetId();
    }

    private void enrichCover(Anniversary anniversary) {
        if (anniversary.getCoverAssetPublicId() != null) {
            var asset = mediaService.findByPublicId(anniversary.getCoverAssetPublicId());
            if (asset != null) anniversary.setCoverMedia(mediaService.toVO(asset));
        }
    }
}
