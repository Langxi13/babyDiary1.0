package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class SpaceAiSchedule {
    private Long spaceId;
    private Boolean weeklyEnabled;
    private Boolean monthlyEnabled;
    private Boolean annualEnabled;
    private Timestamp nextRunAt;
    private Timestamp lastRunAt;
    private Integer updatedBy;
    private Timestamp updatedAt;
}
