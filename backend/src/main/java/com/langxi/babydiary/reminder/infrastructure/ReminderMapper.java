package com.langxi.babydiary.reminder.infrastructure;

import com.langxi.babydiary.reminder.application.ReminderRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReminderMapper {
    @Select(
            "SELECT public_id,type,enabled,schedule,next_run_at,last_run_at FROM reminder WHERE space_id=#{spaceId} AND account_id=#{accountId} ORDER BY type")
    List<ReminderRow> findForAccount(
            @Param("spaceId") long spaceId, @Param("accountId") long accountId);

    @Insert(
            """
            INSERT INTO reminder(public_id,account_id,space_id,type,enabled,schedule,next_run_at,created_at,updated_at)
            VALUES(#{publicId},#{accountId},#{spaceId},#{type},#{enabled},#{scheduleJson},#{nextRunAt},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),schedule=VALUES(schedule),next_run_at=VALUES(next_run_at),updated_at=UTC_TIMESTAMP(6)
            """)
    void upsert(ReminderRepository.NewReminder reminder);

    @Select(
            """
            SELECT r.reminder_id,r.public_id,r.account_id,r.space_id,
                   s.public_id AS space_public_id,s.name AS space_name,r.type,r.schedule,r.next_run_at
            FROM reminder r JOIN diary_space s ON s.space_id=r.space_id
            JOIN account a ON a.account_id=r.account_id
            JOIN space_member m ON m.space_id=r.space_id AND m.account_id=r.account_id
            WHERE r.enabled=1 AND r.next_run_at<=#{now}
              AND s.deleted_at IS NULL AND a.status='ACTIVE' AND m.status='ACTIVE'
            ORDER BY r.next_run_at,r.reminder_id LIMIT #{limit}
            """)
    List<DueRow> findDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update(
            """
            UPDATE reminder SET last_run_at=#{expectedRunAt},next_run_at=#{nextRunAt},updated_at=UTC_TIMESTAMP(6)
            WHERE reminder_id=#{reminderId} AND enabled=1 AND next_run_at=#{expectedRunAt}
            """)
    int claim(
            @Param("reminderId") long reminderId,
            @Param("expectedRunAt") LocalDateTime expectedRunAt,
            @Param("nextRunAt") LocalDateTime nextRunAt);

    @Update(
            """
            UPDATE reminder SET enabled=0,next_run_at=NULL,updated_at=UTC_TIMESTAMP(6)
            WHERE reminder_id=#{reminderId} AND enabled=1 AND next_run_at=#{expectedRunAt}
            """)
    void disable(
            @Param("reminderId") long reminderId,
            @Param("expectedRunAt") LocalDateTime expectedRunAt);

    record DueRow(
            long reminderId,
            byte[] publicId,
            long accountId,
            long spaceId,
            byte[] spacePublicId,
            String spaceName,
            String type,
            String schedule,
            LocalDateTime nextRunAt) {}

    final class ReminderRow {
        private byte[] publicId;
        private String type;
        private boolean enabled;
        private String schedule;
        private LocalDateTime nextRunAt;
        private LocalDateTime lastRunAt;

        public ReminderRow() {}

        public byte[] publicId() {
            return publicId;
        }

        public String type() {
            return type;
        }

        public boolean enabled() {
            return enabled;
        }

        public String schedule() {
            return schedule;
        }

        public LocalDateTime nextRunAt() {
            return nextRunAt;
        }

        public LocalDateTime lastRunAt() {
            return lastRunAt;
        }

        public void setPublicId(byte[] publicId) {
            this.publicId = publicId;
        }

        public void setType(String type) {
            this.type = type;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void setSchedule(String schedule) {
            this.schedule = schedule;
        }

        public void setNextRunAt(LocalDateTime nextRunAt) {
            this.nextRunAt = nextRunAt;
        }

        public void setLastRunAt(LocalDateTime lastRunAt) {
            this.lastRunAt = lastRunAt;
        }
    }
}
