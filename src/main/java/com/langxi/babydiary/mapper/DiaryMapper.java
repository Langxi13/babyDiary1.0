package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.Diary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DiaryMapper {
    void insertDiary(Diary diary);
    List<Diary> findDiariesByDateRange(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate);
    List<String> findImagePathsByDateRange(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate);
    //查找所有日记
    List<Diary> findAllDiaries();
    //根据id查找日记
    Diary findDiaryById(Integer diaryId);
    //根据用户id查找用户名
    String findUsernameByUserId(Integer userId);
    //根据日记id查找用户名
    String findUsernameByDiaryId(Integer diaryId);
    //修改日记
    void updateDiary(Diary diary);
    //删除日记
    void deleteDiary(Integer diaryId);

    //按日期分页查找
    List<Diary> findDiariesPageByDateRange(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("limit") int limit, @Param("offset") long offset);
    int countDiariesByDateRange(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate);
}