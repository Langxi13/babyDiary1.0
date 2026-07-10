package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.DiaryImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiaryImageMapper {
    /*增*/
    //插入单个数据
    void insertDiaryImage(DiaryImage diaryImage);
    // 批量插入图片
    void insertDiaryImages(@Param("diaryImages") DiaryImage[] diaryImages);

    /*删*/
    //根据图片id删除图片
    void deleteDiaryImageById(Integer imageId);
    //根据日记id删除图片
    void deleteDiaryImageByDiaryId(Integer diaryId);

    /*查*/
    //根据diaryId查找数据
    List<DiaryImage> findDiaryImagesByDiaryId(Integer diaryId);

    List<DiaryImage> findDiaryImagesByDiaryIds(@Param("diaryIds") List<Integer> diaryIds);

    //根据diaryId查找图片路径
    List<String> findImagePathsByDiaryId(Integer diaryId);

    //找到用户所有图片，用于到处Zip
    List<String> findImagePathsByUserIdAndDateRange(@Param("userId") int userId,
                                                    @Param("startDate") String startDate,
                                                    @Param("endDate") String endDate);

}
