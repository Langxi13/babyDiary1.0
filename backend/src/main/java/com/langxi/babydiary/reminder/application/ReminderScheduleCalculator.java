package com.langxi.babydiary.reminder.application;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduleCalculator {
    public LocalDateTime nextUtc(
            String type, LocalTime time, Integer dayOfWeek, ZoneId zone, ZonedDateTime now) {
        ZonedDateTime candidate;
        if ("DAILY".equals(type)) {
            candidate = now.toLocalDate().atTime(time).atZone(zone);
            if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
        } else if ("WEEKLY".equals(type) && dayOfWeek != null) {
            LocalDate date =
                    now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfWeek)));
            candidate = date.atTime(time).atZone(zone);
            if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1);
        } else {
            throw new IllegalArgumentException("Reminder schedule is invalid");
        }
        return candidate
                .withSecond(0)
                .withNano(0)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }
}
