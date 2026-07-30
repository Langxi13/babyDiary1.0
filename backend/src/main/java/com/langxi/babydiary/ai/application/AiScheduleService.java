package com.langxi.babydiary.ai.application;

import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiScheduleService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final SpaceAccess spaces;
    private final AiScheduleRepository schedules;

    public AiScheduleService(SpaceAccess spaces, AiScheduleRepository schedules) {
        this.spaces = spaces;
        this.schedules = schedules;
    }

    public Schedule get(UUID spaceId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return response(schedules.find(space.internalId()));
    }

    @Transactional
    public Schedule update(
            UUID spaceId, long accountId, boolean weekly, boolean monthly, boolean annual) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        if (!"OWNER".equals(space.role())) {
            throw ApiException.forbidden("SPACE_OWNER_REQUIRED", "只有空间所有者可以修改自动回顾计划");
        }
        LocalDateTime nextRunAt =
                weekly || monthly || annual
                        ? LocalDateTime.ofInstant(
                                ZonedDateTime.now(ZONE)
                                        .plusDays(1)
                                        .withHour(6)
                                        .withMinute(5)
                                        .withSecond(0)
                                        .withNano(0)
                                        .toInstant(),
                                ZoneOffset.UTC)
                        : null;
        schedules.save(space.internalId(), accountId, weekly, monthly, annual, nextRunAt);
        return response(schedules.find(space.internalId()));
    }

    private Schedule response(AiScheduleRepository.ScheduleState state) {
        return state == null
                ? new Schedule(false, false, false, null)
                : new Schedule(
                        state.weeklyEnabled(),
                        state.monthlyEnabled(),
                        state.annualEnabled(),
                        state.nextRunAt());
    }

    public record Schedule(
            boolean weeklyEnabled,
            boolean monthlyEnabled,
            boolean annualEnabled,
            LocalDateTime nextRunAt) {}
}
