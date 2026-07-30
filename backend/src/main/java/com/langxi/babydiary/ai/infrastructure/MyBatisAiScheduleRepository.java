package com.langxi.babydiary.ai.infrastructure;

import com.langxi.babydiary.ai.application.AiScheduleRepository;
import com.langxi.babydiary.platform.application.BinaryUuid;
import java.time.LocalDateTime;
import java.util.List;
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

    @Override
    public List<DueSchedule> findDue(LocalDateTime now, int limit) {
        return mapper.findDue(now, limit).stream()
                .map(
                        row ->
                                new DueSchedule(
                                        row.spaceId(),
                                        BinaryUuid.fromBytes(row.spacePublicId()),
                                        row.updatedBy(),
                                        row.weeklyEnabled(),
                                        row.monthlyEnabled(),
                                        row.annualEnabled(),
                                        row.nextRunAt()))
                .toList();
    }

    @Override
    public boolean claim(long spaceId, LocalDateTime expectedRunAt, LocalDateTime nextRunAt) {
        return mapper.claim(spaceId, expectedRunAt, nextRunAt) == 1;
    }
}
