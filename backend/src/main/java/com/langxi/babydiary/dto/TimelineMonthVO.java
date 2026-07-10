package com.langxi.babydiary.dto;

import lombok.Data;

import java.util.List;

@Data
public class TimelineMonthVO {
    private String month;
    private List<DiaryVO> diaries;
}
