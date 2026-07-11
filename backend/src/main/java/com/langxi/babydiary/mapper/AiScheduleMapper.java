package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.SpaceAiSchedule;
import org.apache.ibatis.annotations.*;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface AiScheduleMapper {
    @Select("SELECT space_id spaceId,weekly_enabled weeklyEnabled,monthly_enabled monthlyEnabled,annual_enabled annualEnabled," +
            "next_run_at nextRunAt,last_run_at lastRunAt,updated_by updatedBy,updated_at updatedAt " +
            "FROM space_ai_schedule WHERE space_id=#{spaceId}")
    SpaceAiSchedule find(@Param("spaceId") Long spaceId);

    @Insert("INSERT INTO space_ai_schedule(space_id,weekly_enabled,monthly_enabled,annual_enabled,next_run_at,updated_by) " +
            "VALUES(#{spaceId},#{weeklyEnabled},#{monthlyEnabled},#{annualEnabled},#{nextRunAt},#{updatedBy}) " +
            "ON DUPLICATE KEY UPDATE weekly_enabled=VALUES(weekly_enabled),monthly_enabled=VALUES(monthly_enabled)," +
            "annual_enabled=VALUES(annual_enabled),next_run_at=VALUES(next_run_at),updated_by=VALUES(updated_by)")
    void upsert(SpaceAiSchedule schedule);

    @Select("SELECT space_id spaceId,weekly_enabled weeklyEnabled,monthly_enabled monthlyEnabled,annual_enabled annualEnabled," +
            "next_run_at nextRunAt,last_run_at lastRunAt,updated_by updatedBy,updated_at updatedAt " +
            "FROM space_ai_schedule WHERE next_run_at IS NOT NULL AND next_run_at<=NOW() " +
            "AND (weekly_enabled=1 OR monthly_enabled=1 OR annual_enabled=1) ORDER BY next_run_at LIMIT #{limit}")
    List<SpaceAiSchedule> findDue(@Param("limit") int limit);

    @Update("UPDATE space_ai_schedule SET last_run_at=#{expectedRunAt},next_run_at=#{nextRunAt} " +
            "WHERE space_id=#{spaceId} AND next_run_at=#{expectedRunAt}")
    int claimRun(@Param("spaceId") Long spaceId,
                 @Param("expectedRunAt") Timestamp expectedRunAt,
                 @Param("nextRunAt") Timestamp nextRunAt);
}
