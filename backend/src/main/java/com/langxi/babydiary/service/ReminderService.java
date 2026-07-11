package com.langxi.babydiary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.ReminderDTO;
import com.langxi.babydiary.dto.ReminderVO;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.Reminder;
import com.langxi.babydiary.entity.User;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.ReminderMapper;
import com.langxi.babydiary.mapper.SpaceMapper;
import com.langxi.babydiary.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class ReminderService {
    private static final List<String> TYPES = List.of("DAILY", "WEEKLY");
    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Shanghai");

    private final ReminderMapper mapper;
    private final SpaceService spaceService;
    private final SpaceMapper spaceMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public ReminderService(ReminderMapper mapper,
                           SpaceService spaceService,
                           SpaceMapper spaceMapper,
                           UserMapper userMapper,
                           NotificationService notificationService,
                           ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.spaceService = spaceService;
        this.spaceMapper = spaceMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public List<ReminderVO> list(String spacePublicId, Integer userId) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        return mapper.list(userId, space.getSpaceId()).stream().map(this::toVO).toList();
    }

    @Transactional
    public ReminderVO save(String spacePublicId, Integer userId, String type, ReminderDTO dto) {
        String normalizedType = normalizeType(type);
        if ("WEEKLY".equals(normalizedType) && dto.getDayOfWeek() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "每周提醒需要选择星期");
        }
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        Schedule schedule = new Schedule(dto.getTime(), "WEEKLY".equals(normalizedType) ? dto.getDayOfWeek() : null);
        Reminder reminder = new Reminder();
        reminder.setPublicId(UUID.randomUUID().toString());
        reminder.setUserId(userId);
        reminder.setSpaceId(space.getSpaceId());
        reminder.setType(normalizedType);
        reminder.setEnabled(dto.isEnabled());
        reminder.setScheduleJson(writeSchedule(schedule));
        ZoneId zone = zoneFor(userId);
        reminder.setNextRunAt(dto.isEnabled() ? nextRun(normalizedType, schedule, zone, ZonedDateTime.now(zone)) : null);
        mapper.upsert(reminder);
        return mapper.list(userId, space.getSpaceId()).stream()
                .filter(item -> normalizedType.equals(item.getType()))
                .findFirst().map(this::toVO)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "提醒保存失败"));
    }

    @Scheduled(fixedDelayString = "${app.reminders.delivery-delay-ms:60000}")
    public void deliverDue() {
        for (Reminder reminder : mapper.findDue(50)) {
            Timestamp next;
            try {
                Schedule schedule = readSchedule(reminder.getScheduleJson());
                ZoneId zone = zoneFor(reminder.getUserId());
                next = nextRun(reminder.getType(), schedule, zone, ZonedDateTime.now(zone));
            } catch (RuntimeException exception) {
                mapper.disableInvalid(reminder.getReminderId(), reminder.getNextRunAt());
                log.warn("提醒任务已停用: reminderId={}, reason={}", reminder.getReminderId(), exception.getMessage());
                continue;
            }
            if (mapper.claim(reminder.getReminderId(), reminder.getNextRunAt(), next) != 1) continue;
            try {
                DiarySpace space = spaceMapper.findById(reminder.getSpaceId());
                String spaceName = space == null ? "Baby Diary" : space.getName();
                notificationService.notifyUser(reminder.getUserId(), reminder.getSpaceId(), "DIARY_REMINDER",
                        "今天也留下一段回忆吧", spaceName + " 正等着你的新记录", "/spaces",
                        "reminder:" + reminder.getReminderId() + ":" + reminder.getNextRunAt().getTime());
            } catch (RuntimeException exception) {
                log.warn("提醒通知发送失败: reminderId={}, reason={}", reminder.getReminderId(), exception.getMessage());
            }
        }
    }

    private ReminderVO toVO(Reminder reminder) {
        Schedule schedule = readSchedule(reminder.getScheduleJson());
        return new ReminderVO(reminder.getPublicId(), reminder.getType(), Boolean.TRUE.equals(reminder.getEnabled()),
                schedule.time(), schedule.dayOfWeek(), reminder.getNextRunAt(), reminder.getLastRunAt());
    }

    private Timestamp nextRun(String type, Schedule schedule, ZoneId zone, ZonedDateTime now) {
        LocalTime time;
        try {
            time = LocalTime.parse(schedule.time());
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "提醒时间无效");
        }
        ZonedDateTime candidate;
        if ("DAILY".equals(type)) {
            candidate = now.toLocalDate().atTime(time).atZone(zone);
            if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
        } else {
            if (schedule.dayOfWeek() == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "每周提醒缺少星期");
            DayOfWeek target = DayOfWeek.of(schedule.dayOfWeek());
            LocalDate date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(target));
            candidate = date.atTime(time).atZone(zone);
            if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1);
        }
        return Timestamp.from(candidate.withSecond(0).withNano(0).toInstant());
    }

    private ZoneId zoneFor(Integer userId) {
        User user = userMapper.findById(userId);
        try {
            return user == null || user.getTimezone() == null ? FALLBACK_ZONE : ZoneId.of(user.getTimezone());
        } catch (DateTimeException exception) {
            return FALLBACK_ZONE;
        }
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) throw new BusinessException(ErrorCode.BAD_REQUEST, "提醒类型无效");
        return normalized;
    }

    private String writeSchedule(Schedule schedule) {
        try {
            return objectMapper.writeValueAsString(schedule);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "提醒设置序列化失败");
        }
    }

    private Schedule readSchedule(String value) {
        try {
            return objectMapper.readValue(value, Schedule.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "提醒设置数据损坏");
        }
    }

    public record Schedule(String time, Integer dayOfWeek) { }
}
