package com.langxi.babydiary.v3.reminder.infrastructure;

import com.langxi.babydiary.v3.reminder.application.ReminderRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReminderMapper {
    @Select("SELECT public_id,type,enabled,schedule,next_run_at,last_run_at FROM reminder WHERE space_id=#{spaceId} AND account_id=#{accountId} ORDER BY type")
    List<ReminderRow> findForAccount(@Param("spaceId") long spaceId, @Param("accountId") long accountId);

    @Insert("""
            INSERT INTO reminder(public_id,account_id,space_id,type,enabled,schedule,next_run_at,created_at,updated_at)
            VALUES(#{publicId},#{accountId},#{spaceId},#{type},#{enabled},#{scheduleJson},#{nextRunAt},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),schedule=VALUES(schedule),next_run_at=VALUES(next_run_at),updated_at=UTC_TIMESTAMP(6)
            """)
    void upsert(ReminderRepository.NewReminder reminder);

    final class ReminderRow {
        private byte[] publicId;
        private String type;
        private boolean enabled;
        private String schedule;
        private LocalDateTime nextRunAt;
        private LocalDateTime lastRunAt;

        public ReminderRow() {
        }

        public byte[] publicId() { return publicId; }
        public String type() { return type; }
        public boolean enabled() { return enabled; }
        public String schedule() { return schedule; }
        public LocalDateTime nextRunAt() { return nextRunAt; }
        public LocalDateTime lastRunAt() { return lastRunAt; }

        public void setPublicId(byte[] publicId) { this.publicId = publicId; }
        public void setType(String type) { this.type = type; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setSchedule(String schedule) { this.schedule = schedule; }
        public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }
        public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    }
}
