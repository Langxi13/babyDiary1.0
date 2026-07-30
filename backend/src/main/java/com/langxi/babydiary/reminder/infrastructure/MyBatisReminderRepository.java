package com.langxi.babydiary.reminder.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.reminder.application.ReminderRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisReminderRepository implements ReminderRepository {
    private final ReminderMapper mapper;
    private final ObjectMapper json;

    public MyBatisReminderRepository(ReminderMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public List<Row> findForAccount(long spaceId, long accountId) {
        return mapper.findForAccount(spaceId, accountId).stream()
                .map(
                        row -> {
                            try {
                                return new Row(
                                        BinaryUuid.fromBytes(row.publicId()),
                                        row.type(),
                                        row.enabled(),
                                        json.readTree(row.schedule()),
                                        row.nextRunAt(),
                                        row.lastRunAt());
                            } catch (Exception exception) {
                                throw new IllegalStateException(
                                        "Stored reminder schedule is invalid", exception);
                            }
                        })
                .toList();
    }

    @Override
    public void upsert(NewReminder reminder) {
        mapper.upsert(reminder);
    }

    @Override
    public List<DueReminder> findDue(LocalDateTime now, int limit) {
        return mapper.findDue(now, limit).stream()
                .map(
                        row -> {
                            try {
                                return new DueReminder(
                                        row.reminderId(),
                                        BinaryUuid.fromBytes(row.publicId()),
                                        row.accountId(),
                                        row.spaceId(),
                                        BinaryUuid.fromBytes(row.spacePublicId()),
                                        row.spaceName(),
                                        row.type(),
                                        json.readTree(row.schedule()),
                                        row.nextRunAt());
                            } catch (Exception exception) {
                                throw new IllegalStateException(
                                        "Stored reminder schedule is invalid", exception);
                            }
                        })
                .toList();
    }

    @Override
    public boolean claim(long reminderId, LocalDateTime expectedRunAt, LocalDateTime nextRunAt) {
        return mapper.claim(reminderId, expectedRunAt, nextRunAt) == 1;
    }

    @Override
    public void disable(long reminderId, LocalDateTime expectedRunAt) {
        mapper.disable(reminderId, expectedRunAt);
    }
}
