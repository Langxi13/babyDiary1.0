package com.langxi.babydiary.entity;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class AiAlbumProposal {
    private Integer proposalId;
    private Integer userId;
    private String status;
    private Date startDate;
    private Date endDate;
    private String prompt;
    private String contentJson;
    private String model;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
