package com.langxi.babydiary.reminder.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.reminder.application.ReminderRepository;
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
}
