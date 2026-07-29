package com.langxi.babydiary.v3.reminder.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.v3.platform.application.BinaryUuid;
import com.langxi.babydiary.v3.reminder.application.ReminderRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

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
        return mapper.findForAccount(spaceId, accountId).stream().map(row -> {
            try {
                return new Row(BinaryUuid.fromBytes(row.publicId()), row.type(), row.enabled(), json.readTree(row.schedule()),
                        row.nextRunAt(), row.lastRunAt());
            } catch (Exception exception) {
                throw new IllegalStateException("Stored reminder schedule is invalid", exception);
            }
        }).toList();
    }

    @Override
    public void upsert(NewReminder reminder) {
        mapper.upsert(reminder);
    }
}
