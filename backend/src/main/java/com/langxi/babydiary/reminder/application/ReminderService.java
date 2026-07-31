package com.langxi.babydiary.reminder.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.langxi.babydiary.identity.application.ProfileRepository;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {
    private final SpaceAccess spaces;
    private final ReminderRepository reminders;
    private final ObjectMapper json;
    private final ProfileRepository profiles;
    private final ReminderScheduleCalculator scheduleCalculator;

    public ReminderService(
            SpaceAccess spaces,
            ReminderRepository reminders,
            ObjectMapper json,
            ProfileRepository profiles,
            ReminderScheduleCalculator scheduleCalculator) {
        this.spaces = spaces;
        this.reminders = reminders;
        this.json = json;
        this.profiles = profiles;
        this.scheduleCalculator = scheduleCalculator;
    }

    public List<ReminderView> list(UUID spaceId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return reminders.findForAccount(space.internalId(), accountId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public ReminderView save(
            UUID spaceId,
            long accountId,
            String type,
            String time,
            Integer dayOfWeek,
            boolean enabled) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!"DAILY".equals(normalized) && !"WEEKLY".equals(normalized)) {
            throw ApiException.badRequest("REMINDER_TYPE_INVALID", "提醒类型无效");
        }
        LocalTime parsed;
        try {
            parsed = LocalTime.parse(time);
        } catch (Exception exception) {
            throw ApiException.badRequest("REMINDER_TIME_INVALID", "提醒时间无效");
        }
        if ("WEEKLY".equals(normalized) && (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7)) {
            throw ApiException.badRequest("REMINDER_DAY_INVALID", "每周提醒需要选择1至7的星期");
        }
        ObjectNode schedule = json.createObjectNode().put("time", parsed.toString());
        if ("WEEKLY".equals(normalized)) schedule.put("dayOfWeek", dayOfWeek);
        ZoneId zone;
        try {
            zone =
                    ZoneId.of(
                            profiles.find(accountId)
                                    .map(ProfileRepository.Profile::timezone)
                                    .orElse("Asia/Shanghai"));
        } catch (Exception exception) {
            zone = ZoneId.of("Asia/Shanghai");
        }
        LocalDateTime next =
                enabled
                        ? scheduleCalculator.nextUtc(
                                normalized, parsed, dayOfWeek, zone, ZonedDateTime.now(zone))
                        : null;
        reminders.upsert(
                new ReminderRepository.NewReminder(
                        BinaryUuid.toBytes(UUID.randomUUID()),
                        accountId,
                        space.internalId(),
                        normalized,
                        enabled,
                        schedule.toString(),
                        next));
        return reminders.findForAccount(space.internalId(), accountId).stream()
                .filter(row -> normalized.equals(row.type()))
                .findFirst()
                .map(this::toView)
                .orElseThrow(() -> new IllegalStateException("Reminder was not saved"));
    }

    private ReminderView toView(ReminderRepository.Row row) {
        return new ReminderView(
                row.id(),
                row.type(),
                row.enabled(),
                row.schedule() == null ? null : row.schedule().deepCopy(),
                row.nextRunAt(),
                row.lastRunAt());
    }

    public record ReminderView(
            UUID id,
            String type,
            boolean enabled,
            com.fasterxml.jackson.databind.JsonNode schedule,
            LocalDateTime nextRunAt,
            LocalDateTime lastRunAt) {}
}
