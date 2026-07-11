package com.langxi.babydiary.service;

import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiPeriodResolver {

    private static final Pattern WEEK_PATTERN = Pattern.compile("^(\\d{4})-W(\\d{2})$");
    private static final Pattern MONTH_PATTERN = Pattern.compile("^(\\d{4})-(\\d{2})$");
    private static final Pattern YEAR_PATTERN = Pattern.compile("^(\\d{4})$");

    public AiReportPeriod resolve(String type, String period) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if ("WEEKLY".equals(normalizedType)) {
            return resolveWeek(period);
        }
        if ("MONTHLY".equals(normalizedType)) {
            return resolveMonth(period);
        }
        if ("ANNUAL".equals(normalizedType)) {
            return resolveYear(period);
        }
        throw new IllegalArgumentException("报告类型仅支持 WEEKLY、MONTHLY 或 ANNUAL");
    }

    private AiReportPeriod resolveWeek(String period) {
        Matcher matcher = WEEK_PATTERN.matcher(period == null ? "" : period.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("周报周期格式应为 yyyy-Www，例如 2026-W27");
        }
        int year = Integer.parseInt(matcher.group(1));
        int week = Integer.parseInt(matcher.group(2));
        WeekFields weekFields = WeekFields.ISO;
        LocalDate monday = LocalDate.of(year, 1, 4)
                .with(weekFields.weekBasedYear(), year)
                .with(weekFields.weekOfWeekBasedYear(), week)
                .with(DayOfWeek.MONDAY);
        return new AiReportPeriod("WEEKLY", String.format("%04d-W%02d", year, week), Date.valueOf(monday), Date.valueOf(monday.plusDays(6)));
    }

    private AiReportPeriod resolveMonth(String period) {
        Matcher matcher = MONTH_PATTERN.matcher(period == null ? "" : period.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("月报周期格式应为 yyyy-MM，例如 2026-07");
        }
        YearMonth month = YearMonth.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        return new AiReportPeriod("MONTHLY", month.toString(), Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()));
    }

    private AiReportPeriod resolveYear(String period) {
        Matcher matcher = YEAR_PATTERN.matcher(period == null ? "" : period.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("年报周期格式应为 yyyy，例如 2026");
        }
        int year = Integer.parseInt(matcher.group(1));
        LocalDate firstDay = LocalDate.of(year, 1, 1);
        return new AiReportPeriod("ANNUAL", String.valueOf(year), Date.valueOf(firstDay), Date.valueOf(firstDay.withMonth(12).withDayOfMonth(31)));
    }
}
