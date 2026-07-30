package com.langxi.babydiary.ai.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AiScheduleMapper {
    @Select(
            "SELECT weekly_enabled,monthly_enabled,annual_enabled,next_run_at,last_run_at,updated_at FROM space_ai_schedule WHERE space_id=#{spaceId}")
    ScheduleRow find(long spaceId);

    @Insert(
            """
            INSERT INTO space_ai_schedule(space_id,weekly_enabled,monthly_enabled,annual_enabled,next_run_at,updated_by)
            VALUES(#{spaceId},#{weekly},#{monthly},#{annual},#{nextRunAt},#{accountId})
            ON DUPLICATE KEY UPDATE weekly_enabled=VALUES(weekly_enabled),monthly_enabled=VALUES(monthly_enabled),
              annual_enabled=VALUES(annual_enabled),next_run_at=VALUES(next_run_at),updated_by=VALUES(updated_by),updated_at=UTC_TIMESTAMP(6)
            """)
    void upsert(
            @Param("spaceId") long spaceId,
            @Param("accountId") long accountId,
            @Param("weekly") boolean weekly,
            @Param("monthly") boolean monthly,
            @Param("annual") boolean annual,
            @Param("nextRunAt") LocalDateTime nextRunAt);

    @Select(
            """
            SELECT a.space_id,s.public_id AS space_public_id,a.updated_by,
                   a.weekly_enabled,a.monthly_enabled,a.annual_enabled,a.next_run_at
            FROM space_ai_schedule a JOIN diary_space s ON s.space_id=a.space_id
            JOIN space_member m ON m.space_id=a.space_id AND m.account_id=a.updated_by
            WHERE a.next_run_at<=#{now} AND s.deleted_at IS NULL AND m.status='ACTIVE'
              AND (a.weekly_enabled=1 OR a.monthly_enabled=1 OR a.annual_enabled=1)
            ORDER BY a.next_run_at,a.space_id LIMIT #{limit}
            """)
    List<DueRow> findDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update(
            """
            UPDATE space_ai_schedule SET last_run_at=#{expectedRunAt},next_run_at=#{nextRunAt}
            WHERE space_id=#{spaceId} AND next_run_at=#{expectedRunAt}
            """)
    int claim(
            @Param("spaceId") long spaceId,
            @Param("expectedRunAt") LocalDateTime expectedRunAt,
            @Param("nextRunAt") LocalDateTime nextRunAt);

    record DueRow(
            long spaceId,
            byte[] spacePublicId,
            long updatedBy,
            boolean weeklyEnabled,
            boolean monthlyEnabled,
            boolean annualEnabled,
            LocalDateTime nextRunAt) {}

    record ScheduleRow(
            boolean weeklyEnabled,
            boolean monthlyEnabled,
            boolean annualEnabled,
            LocalDateTime nextRunAt,
            LocalDateTime lastRunAt,
            LocalDateTime updatedAt) {}
}
