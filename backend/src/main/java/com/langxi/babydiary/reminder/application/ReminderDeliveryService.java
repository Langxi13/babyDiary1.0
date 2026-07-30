package com.langxi.babydiary.reminder.application;

import com.langxi.babydiary.identity.application.ProfileRepository;
import com.langxi.babydiary.notification.application.NotificationDeliveryService;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderDeliveryService {
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Shanghai");

    private final ReminderRepository reminders;
    private final ProfileRepository profiles;
    private final ReminderScheduleCalculator schedules;
    private final NotificationDeliveryService notifications;

    public ReminderDeliveryService(
            ReminderRepository reminders,
            ProfileRepository profiles,
            ReminderScheduleCalculator schedules,
            NotificationDeliveryService notifications) {
        this.reminders = reminders;
        this.profiles = profiles;
        this.schedules = schedules;
        this.notifications = notifications;
    }

    @Transactional
    public boolean deliver(ReminderRepository.DueReminder reminder) {
        String time = reminder.schedule().path("time").asText("");
        Integer day =
                reminder.schedule().hasNonNull("dayOfWeek")
                        ? reminder.schedule().path("dayOfWeek").asInt()
                        : null;
        LocalTime parsed = LocalTime.parse(time);
        ZoneId zone = zoneFor(reminder.accountId());
        var next = schedules.nextUtc(reminder.type(), parsed, day, zone, ZonedDateTime.now(zone));
        if (!reminders.claim(reminder.internalId(), reminder.nextRunAt(), next)) return false;
        return notifications.notifyUser(
                reminder.accountId(),
                reminder.spaceInternalId(),
                "DIARY_REMINDER",
                "今天也留下一段回忆吧",
                reminder.spaceName() + " 正等着你的新记录",
                "/spaces/" + reminder.spaceId(),
                "reminder:" + reminder.id() + ":" + reminder.nextRunAt());
    }

    @Transactional
    public void disable(ReminderRepository.DueReminder reminder) {
        reminders.disable(reminder.internalId(), reminder.nextRunAt());
    }

    private ZoneId zoneFor(long accountId) {
        try {
            return ZoneId.of(
                    profiles.find(accountId)
                            .map(ProfileRepository.Profile::timezone)
                            .orElse(FALLBACK_ZONE.getId()));
        } catch (Exception exception) {
            return FALLBACK_ZONE;
        }
    }
}
