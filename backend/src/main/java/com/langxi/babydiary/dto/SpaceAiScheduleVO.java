package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.SpaceAiSchedule;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class SpaceAiScheduleVO {
    private boolean weeklyEnabled;
    private boolean monthlyEnabled;
    private boolean annualEnabled;
    private Timestamp nextRunAt;

    public static SpaceAiScheduleVO from(SpaceAiSchedule schedule) {
        SpaceAiScheduleVO vo = new SpaceAiScheduleVO();
        if (schedule != null) {
            vo.setWeeklyEnabled(Boolean.TRUE.equals(schedule.getWeeklyEnabled()));
            vo.setMonthlyEnabled(Boolean.TRUE.equals(schedule.getMonthlyEnabled()));
            vo.setAnnualEnabled(Boolean.TRUE.equals(schedule.getAnnualEnabled()));
            vo.setNextRunAt(schedule.getNextRunAt());
        }
        return vo;
    }
}
