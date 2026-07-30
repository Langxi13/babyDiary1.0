package com.langxi.babydiary.ai.infrastructure;

import com.langxi.babydiary.ai.application.AiScheduleRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAiScheduleRepository implements AiScheduleRepository {
    private final AiScheduleMapper mapper;

    public MyBatisAiScheduleRepository(AiScheduleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ScheduleState find(long spaceId) {
        AiScheduleMapper.ScheduleRow row = mapper.find(spaceId);
        return row == null
                ? null
                : new ScheduleState(
                        row.weeklyEnabled(),
                        row.monthlyEnabled(),
                        row.annualEnabled(),
                        row.nextRunAt());
    }

    @Override
    public void save(
            long spaceId,
            long accountId,
            boolean weeklyEnabled,
            boolean monthlyEnabled,
            boolean annualEnabled,
            LocalDateTime nextRunAt) {
        mapper.upsert(spaceId, accountId, weeklyEnabled, monthlyEnabled, annualEnabled, nextRunAt);
    }
}
