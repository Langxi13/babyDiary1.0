package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.Diary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiaryMapper {
    void insertDiary(Diary diary);
    Diary findDiaryById(Integer diaryId);
    void updateDiary(Diary diary);
    void deleteDiary(Integer diaryId);

    List<Diary> findDiariesPageByDateRange(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("tagId") Integer tagId, @Param("moodKey") String moodKey, @Param("limit") int limit, @Param("offset") long offset);
    List<Diary> findDiarySummariesPageByDateRange(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("tagId") Integer tagId, @Param("moodKey") String moodKey, @Param("limit") int limit, @Param("offset") long offset);
    int countDiariesByDateRange(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("tagId") Integer tagId, @Param("moodKey") String moodKey);

    List<Diary> findDiariesPageByDateRangeAndKeyword(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("searchKeyword") String searchKeyword, @Param("tagId") Integer tagId, @Param("moodKey") String moodKey, @Param("limit") int limit, @Param("offset") long offset);
    List<Diary> findDiarySummariesPageByDateRangeAndKeyword(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("searchKeyword") String searchKeyword, @Param("tagId") Integer tagId, @Param("moodKey") String moodKey, @Param("limit") int limit, @Param("offset") long offset);
    int countDiariesByDateRangeAndKeyword(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("searchKeyword") String searchKeyword, @Param("tagId") Integer tagId, @Param("moodKey") String moodKey);

    List<Diary> findDiariesForTimeline(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate, @Param("tagId") Integer tagId, @Param("moodKey") String moodKey);

    List<Diary> findDiariesForReport(@Param("userId") Integer userId, @Param("startDate") java.sql.Date startDate, @Param("endDate") java.sql.Date endDate);

    List<com.langxi.babydiary.dto.CalendarDayVO> findCalendarDays(@Param("userId") Integer userId, @Param("startDate") String startDate, @Param("endDate") String endDate);
}
