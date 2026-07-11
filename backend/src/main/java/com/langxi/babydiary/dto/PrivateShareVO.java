package com.langxi.babydiary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
public class PrivateShareVO {
    private String shareId;
    private String sharePath;
    private Timestamp expiresAt;
    private Integer maxViews;
}
