package com.langxi.babydiary.dto;

import lombok.Data;

import java.sql.Date;

@Data
public class SearchResultVO {
    private String entityType;
    private String entityId;
    private String title;
    private String snippet;
    private Date date;
    private Double score;
}
