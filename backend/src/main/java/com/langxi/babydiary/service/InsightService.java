package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.InsightDayVO;
import com.langxi.babydiary.dto.InsightVO;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.InsightMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class InsightService {
    private final InsightMapper mapper;
    private final SpaceService spaceService;

    public InsightService(InsightMapper mapper, SpaceService spaceService) {
        this.mapper = mapper;
        this.spaceService = spaceService;
    }

    public InsightVO yearly(String spacePublicId, Integer userId, int year) {
        if (year < 1900 || year > LocalDate.now().getYear() + 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "统计年份无效");
        }
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        String start = LocalDate.of(year, 1, 1).toString();
        String end = LocalDate.of(year, 12, 31).toString();
        List<InsightDayVO> days = mapper.findDays(space.getSpaceId(), userId, start, end);
        int diaryCount = days.stream().mapToInt(InsightDayVO::getCount).sum();
        Streak streak = streak(days);
        return new InsightVO(year, diaryCount, days.size(), streak.current, streak.longest,
                mapper.countPhotos(space.getSpaceId(), userId, start, end), days,
                mapper.findMoods(space.getSpaceId(), userId, start, end),
                mapper.findMonths(space.getSpaceId(), userId, start, end));
    }

    private Streak streak(List<InsightDayVO> days) {
        Set<LocalDate> active = new HashSet<>();
        days.forEach(day -> active.add(LocalDate.parse(day.getDate())));
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate date : active.stream().sorted().toList()) {
            run = previous != null && date.equals(previous.plusDays(1)) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = date;
        }
        LocalDate cursor = LocalDate.now();
        if (!active.contains(cursor)) cursor = cursor.minusDays(1);
        int current = 0;
        while (active.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }
        return new Streak(current, longest);
    }

    private record Streak(int current, int longest) {}
}
