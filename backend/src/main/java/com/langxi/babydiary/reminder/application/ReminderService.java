package com.langxi.babydiary.reminder.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.langxi.babydiary.identity.application.ProfileRepository;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
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

    public ReminderService(
            SpaceAccess spaces,
            ReminderRepository reminders,
            ObjectMapper json,
            ProfileRepository profiles) {
        this.spaces = spaces;
        this.reminders = reminders;
        this.json = json;
        this.profiles = profiles;
    }

    public List<ReminderRepository.Row> list(UUID spaceId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return reminders.findForAccount(space.internalId(), accountId);
    }

    @Transactional
    public ReminderRepository.Row save(
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
                enabled ? next(normalized, parsed, dayOfWeek, ZonedDateTime.now(zone)) : null;
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
                .orElseThrow(() -> new IllegalStateException("Reminder was not saved"));
    }

    private LocalDateTime next(String type, LocalTime time, Integer day, ZonedDateTime now) {
        ZonedDateTime candidate;
        if ("DAILY".equals(type)) {
            candidate = now.toLocalDate().atTime(time).atZone(now.getZone());
            if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
        } else {
            LocalDate date =
                    now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day)));
            candidate = date.atTime(time).atZone(now.getZone());
            if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1);
        }
        return candidate
                .withSecond(0)
                .withNano(0)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
