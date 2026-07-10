package com.langxi.babydiary.dto;

import lombok.Data;

@Data
public class CalendarDayVO {
    private String date;
    private Integer count;
    private Integer firstDiaryId;
    private String firstTitle;
}
