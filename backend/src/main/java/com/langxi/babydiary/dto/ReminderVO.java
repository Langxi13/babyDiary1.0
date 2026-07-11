package com.langxi.babydiary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
public class ReminderVO {
    private String reminderId;
    private String type;
    private boolean enabled;
    private String time;
    private Integer dayOfWeek;
    private Timestamp nextRunAt;
    private Timestamp lastRunAt;
}
