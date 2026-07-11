package com.langxi.babydiary.mapper;

import com.langxi.babydiary.dto.InsightDayVO;
import com.langxi.babydiary.dto.MonthStatVO;
import com.langxi.babydiary.dto.MoodStatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InsightMapper {
    String VISIBLE = "d.space_id=#{spaceId} AND d.deleted_at IS NULL AND (d.visibility='SHARED' OR d.user_id=#{userId}) " +
            "AND d.locked=0 AND d.date BETWEEN #{startDate} AND #{endDate}";

    @Select("SELECT DATE_FORMAT(d.date,'%Y-%m-%d') date,COUNT(*) count FROM diary d WHERE " + VISIBLE +
            " GROUP BY d.date ORDER BY d.date")
    List<InsightDayVO> findDays(@Param("spaceId") Long spaceId,
                                @Param("userId") Integer userId,
                                @Param("startDate") String startDate,
                                @Param("endDate") String endDate);

    @Select("SELECT d.mood_key moodKey,COUNT(*) count FROM diary d WHERE " + VISIBLE +
            " AND d.mood_key IS NOT NULL GROUP BY d.mood_key ORDER BY count DESC")
    List<MoodStatVO> findMoods(@Param("spaceId") Long spaceId,
                               @Param("userId") Integer userId,
                               @Param("startDate") String startDate,
                               @Param("endDate") String endDate);

    @Select("SELECT DATE_FORMAT(d.date,'%Y-%m') month,COUNT(*) count FROM diary d WHERE " + VISIBLE +
            " GROUP BY DATE_FORMAT(d.date,'%Y-%m') ORDER BY month")
    List<MonthStatVO> findMonths(@Param("spaceId") Long spaceId,
                                 @Param("userId") Integer userId,
                                 @Param("startDate") String startDate,
                                 @Param("endDate") String endDate);

    @Select("SELECT COUNT(*) FROM diary_image i INNER JOIN diary d ON d.diary_id=i.diary_id WHERE " + VISIBLE)
    int countPhotos(@Param("spaceId") Long spaceId,
                    @Param("userId") Integer userId,
                    @Param("startDate") String startDate,
                    @Param("endDate") String endDate);
}
