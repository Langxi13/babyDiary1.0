package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class Reminder {
    private Long reminderId;
    private String publicId;
    private Integer userId;
    private Long spaceId;
    private String type;
    private Boolean enabled;
    private String scheduleJson;
    private Timestamp nextRunAt;
    private Timestamp lastRunAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
