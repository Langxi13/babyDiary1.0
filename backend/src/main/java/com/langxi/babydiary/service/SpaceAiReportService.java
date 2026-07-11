package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Pagination;
import com.langxi.babydiary.dto.*;
import com.langxi.babydiary.entity.AiReport;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.SpaceAiSchedule;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AiReportMapper;
import com.langxi.babydiary.mapper.AiScheduleMapper;
import com.langxi.babydiary.mapper.SpaceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class SpaceAiReportService {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final SpaceService spaceService;
    private final SpaceMapper spaceMapper;
    private final AiScheduleMapper scheduleMapper;
    private final AiReportMapper reportMapper;
    private final AiReportService reportService;

    public SpaceAiReportService(SpaceService spaceService,
                                SpaceMapper spaceMapper,
                                AiScheduleMapper scheduleMapper,
                                AiReportMapper reportMapper,
                                AiReportService reportService) {
        this.spaceService = spaceService;
        this.spaceMapper = spaceMapper;
        this.scheduleMapper = scheduleMapper;
        this.reportMapper = reportMapper;
        this.reportService = reportService;
    }

    public AiReportVO generate(String spacePublicId, Integer userId, AiReportGenerateDTO dto) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        return AiReportVO.fromEntity(reportService.generateForSpace(
                userId, space.getSpaceId(), "PERSONAL".equals(space.getType()), dto));
    }

    public PageResult<AiReportVO> list(String spacePublicId, Integer userId, String type, int page, int size) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        int normalizedPage = Pagination.normalizePage(page);
        int normalizedSize = Pagination.normalizeSize(size);
        String normalizedType = type == null || type.isBlank() ? null : type.trim().toUpperCase(Locale.ROOT);
        if (normalizedType != null && !List.of("WEEKLY", "MONTHLY", "ANNUAL").contains(normalizedType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报告类型无效");
        }
        return new PageResult<>(reportMapper.findSpacePage(space.getSpaceId(), normalizedType, normalizedSize,
                        Pagination.offset(normalizedPage, normalizedSize)).stream().map(AiReportVO::fromEntity).toList(),
                normalizedPage, normalizedSize, (long) reportMapper.countSpace(space.getSpaceId(), normalizedType));
    }

    public AiReportVO find(String spacePublicId, Integer userId, Integer reportId) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        AiReport report = reportMapper.findSpaceReport(space.getSpaceId(), reportId);
        if (report == null) throw new BusinessException(ErrorCode.AI_REPORT_NOT_FOUND);
        return AiReportVO.fromEntity(report);
    }

    public SpaceAiScheduleVO schedule(String spacePublicId, Integer userId) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        return SpaceAiScheduleVO.from(scheduleMapper.find(space.getSpaceId()));
    }

    @Transactional
    public SpaceAiScheduleVO updateSchedule(String spacePublicId, Integer userId, SpaceAiScheduleDTO dto) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireOwner(spacePublicId, userId);
        SpaceAiSchedule schedule = new SpaceAiSchedule();
        schedule.setSpaceId(space.getSpaceId());
        schedule.setWeeklyEnabled(dto.isWeeklyEnabled());
        schedule.setMonthlyEnabled(dto.isMonthlyEnabled());
        schedule.setAnnualEnabled(dto.isAnnualEnabled());
        schedule.setUpdatedBy(userId);
        schedule.setNextRunAt(anyEnabled(dto) ? nextDailyRun() : null);
        scheduleMapper.upsert(schedule);
        return SpaceAiScheduleVO.from(schedule);
    }

    @Scheduled(cron = "${app.ai.schedule-cron:0 5 6 * * *}", zone = "Asia/Shanghai")
    public void runSchedules() {
        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        for (SpaceAiSchedule schedule : scheduleMapper.findDue(20)) {
            DiarySpace space = spaceMapper.findById(schedule.getSpaceId());
            if (space == null) continue;
            if (scheduleMapper.claimRun(space.getSpaceId(), schedule.getNextRunAt(), nextDailyRun()) != 1) continue;
            try {
                if (Boolean.TRUE.equals(schedule.getWeeklyEnabled()) && today.getDayOfWeek() == DayOfWeek.MONDAY) {
                    generateScheduled(space, schedule, "WEEKLY", previousWeek(today));
                }
                if (Boolean.TRUE.equals(schedule.getMonthlyEnabled()) && today.getDayOfMonth() == 1) {
                    generateScheduled(space, schedule, "MONTHLY", YearMonth.from(today.minusMonths(1)).toString());
                }
                if (Boolean.TRUE.equals(schedule.getAnnualEnabled()) && today.getDayOfYear() == 1) {
                    generateScheduled(space, schedule, "ANNUAL", String.valueOf(today.getYear() - 1));
                }
            } catch (Exception exception) {
                log.warn("空间AI定时报告失败: spaceId={}, reason={}", space.getPublicId(), exception.getMessage());
            }
        }
    }

    private void generateScheduled(DiarySpace space, SpaceAiSchedule schedule, String type, String period) {
        if (reportMapper.existsSpacePeriod(space.getSpaceId(), type, period) > 0) return;
        AiReportGenerateDTO dto = new AiReportGenerateDTO();
        dto.setType(type);
        dto.setPeriod(period);
        reportService.generateForSpace(schedule.getUpdatedBy(), space.getSpaceId(),
                "PERSONAL".equals(space.getType()), dto);
    }

    private String previousWeek(LocalDate today) {
        LocalDate date = today.minusWeeks(1);
        WeekFields fields = WeekFields.ISO;
        return String.format("%04d-W%02d", date.get(fields.weekBasedYear()), date.get(fields.weekOfWeekBasedYear()));
    }

    private boolean anyEnabled(SpaceAiScheduleDTO dto) {
        return dto.isWeeklyEnabled() || dto.isMonthlyEnabled() || dto.isAnnualEnabled();
    }

    private Timestamp nextDailyRun() {
        ZonedDateTime next = ZonedDateTime.now(DEFAULT_ZONE).plusDays(1).withHour(6).withMinute(5).withSecond(0).withNano(0);
        return Timestamp.from(next.toInstant());
    }
}
