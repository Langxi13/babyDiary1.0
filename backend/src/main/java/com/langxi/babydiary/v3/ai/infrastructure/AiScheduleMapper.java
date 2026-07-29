package com.langxi.babydiary.v3.ai.infrastructure;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface AiScheduleMapper {
    @Select("SELECT weekly_enabled,monthly_enabled,annual_enabled,next_run_at,last_run_at,updated_at FROM space_ai_schedule WHERE space_id=#{spaceId}")
    ScheduleRow find(long spaceId);
    @Insert("""
            INSERT INTO space_ai_schedule(space_id,weekly_enabled,monthly_enabled,annual_enabled,next_run_at,updated_by)
            VALUES(#{spaceId},#{weekly},#{monthly},#{annual},#{nextRunAt},#{accountId})
            ON DUPLICATE KEY UPDATE weekly_enabled=VALUES(weekly_enabled),monthly_enabled=VALUES(monthly_enabled),
              annual_enabled=VALUES(annual_enabled),next_run_at=VALUES(next_run_at),updated_by=VALUES(updated_by),updated_at=UTC_TIMESTAMP(6)
            """)
    void upsert(@Param("spaceId") long spaceId,@Param("accountId") long accountId,@Param("weekly") boolean weekly,
                @Param("monthly") boolean monthly,@Param("annual") boolean annual,@Param("nextRunAt") LocalDateTime nextRunAt);
    record ScheduleRow(boolean weeklyEnabled,boolean monthlyEnabled,boolean annualEnabled,LocalDateTime nextRunAt,LocalDateTime lastRunAt,LocalDateTime updatedAt){}
}
