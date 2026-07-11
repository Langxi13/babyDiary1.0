package com.langxi.babydiary.dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class SyncChangeVO {
    private Long cursor;
    private String entityType;
    private String entityId;
    private String operation;
    private Integer revision;
    private Integer actorUserId;
    private String payloadJson;
    private Timestamp createdAt;
}
