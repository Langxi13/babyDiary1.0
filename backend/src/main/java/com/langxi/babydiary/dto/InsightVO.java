package com.langxi.babydiary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class InsightVO {
    private int year;
    private int diaryCount;
    private int activeDays;
    private int currentStreak;
    private int longestStreak;
    private int photoCount;
    private List<InsightDayVO> heatmap;
    private List<MoodStatVO> moods;
    private List<MonthStatVO> months;
}
