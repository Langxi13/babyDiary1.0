package com.langxi.babydiary.service;

import com.langxi.babydiary.entity.DiaryImage;
import com.langxi.babydiary.mapper.DiaryImageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DiaryImageService {

    @Autowired
    private DiaryImageMapper diaryImageMapper;

    /**
     * 插入单个 DiaryImage
     *
     * @param diaryImage 图片信息
     */
    @Transactional
    public void insertDiaryImage(DiaryImage diaryImage) {
        diaryImageMapper.insertDiaryImage(diaryImage);
    }

    /**
     * 批量插入 DiaryImage
     *
     * @param diaryImages 图片信息数组
     */
    @Transactional
    public void insertDiaryImages(DiaryImage[] diaryImages) {
        diaryImageMapper.insertDiaryImages(diaryImages);
    }

    /**
     * 根据图片 ID 删除图片
     *
     * @param imageId 图片 ID
     */
    @Transactional
    public void deleteDiaryImageById(Integer imageId) {
        diaryImageMapper.deleteDiaryImageById(imageId);
    }

    /**
     * 根据日记 ID 删除图片
     *
     * @param diaryId 日记 ID
     */
    @Transactional
    public void deleteDiaryImageByDiaryId(Integer diaryId) {
        diaryImageMapper.deleteDiaryImageByDiaryId(diaryId);
    }

    /**
     * 根据日记 ID 查找图片
     *
     * @param diaryId 日记 ID
     * @return 图片信息
     */
    public List<DiaryImage> findDiaryImageByDiaryId(Integer diaryId) {
        return diaryImageMapper.findDiaryImagesByDiaryId(diaryId);
    }

    /**
     * 根据diary ID 查找图片路径
     */
    public List<String> findImagePathsByDiaryId(Integer diaryId) {
        return diaryImageMapper.findImagePathsByDiaryId(diaryId);
    }

    /**
     * 根据用户 ID 和日期范围查找图片路径
     *
     * @param userId    用户 ID
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 图片路径列表
     */
    public List<String> findImagePathsByUserIdAndDateRange(Integer userId, String startDate, String endDate) {
        return diaryImageMapper.findImagePathsByUserIdAndDateRange(userId, startDate, endDate);
    }
}