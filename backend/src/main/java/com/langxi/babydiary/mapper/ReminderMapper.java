package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.Reminder;
import org.apache.ibatis.annotations.*;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface ReminderMapper {
    String COLUMNS = "reminder_id reminderId,public_id publicId,user_id userId,space_id spaceId,type,enabled," +
            "schedule_json scheduleJson,next_run_at nextRunAt,last_run_at lastRunAt,created_at createdAt,updated_at updatedAt";

    @Select("SELECT " + COLUMNS + " FROM reminder WHERE user_id=#{userId} AND space_id=#{spaceId} ORDER BY type")
    List<Reminder> list(@Param("userId") Integer userId, @Param("spaceId") Long spaceId);

    @Insert("INSERT INTO reminder(public_id,user_id,space_id,type,enabled,schedule_json,next_run_at) " +
            "VALUES(#{publicId},#{userId},#{spaceId},#{type},#{enabled},#{scheduleJson},#{nextRunAt}) " +
            "ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),schedule_json=VALUES(schedule_json)," +
            "next_run_at=VALUES(next_run_at)")
    void upsert(Reminder reminder);

    @Select("SELECT " + COLUMNS + " FROM reminder WHERE enabled=1 AND next_run_at IS NOT NULL " +
            "AND next_run_at<=NOW() ORDER BY next_run_at,reminder_id LIMIT #{limit}")
    List<Reminder> findDue(@Param("limit") int limit);

    @Update("UPDATE reminder SET last_run_at=#{expectedRunAt},next_run_at=#{nextRunAt} " +
            "WHERE reminder_id=#{reminderId} AND enabled=1 AND next_run_at=#{expectedRunAt}")
    int claim(@Param("reminderId") Long reminderId,
              @Param("expectedRunAt") Timestamp expectedRunAt,
              @Param("nextRunAt") Timestamp nextRunAt);

    @Update("UPDATE reminder SET enabled=0,next_run_at=NULL WHERE reminder_id=#{reminderId} AND next_run_at=#{expectedRunAt}")
    int disableInvalid(@Param("reminderId") Long reminderId, @Param("expectedRunAt") Timestamp expectedRunAt);
}
