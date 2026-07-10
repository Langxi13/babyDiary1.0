package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.AiReport;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class AiReportVO {
    private Integer reportId;
    private Integer userId;
    private String type;
    private String period;
    private String periodStart;
    private String periodEnd;
    private String title;
    private String contentMarkdown;
    private Integer diaryCount;
    private String model;
    private Timestamp createdAt;

    public static AiReportVO fromEntity(AiReport report) {
        AiReportVO vo = new AiReportVO();
        vo.setReportId(report.getReportId());
        vo.setUserId(report.getUserId());
        vo.setType(report.getType());
        vo.setPeriod(report.getPeriod());
        vo.setPeriodStart(report.getPeriodStart() == null ? null : report.getPeriodStart().toString());
        vo.setPeriodEnd(report.getPeriodEnd() == null ? null : report.getPeriodEnd().toString());
        vo.setTitle(report.getTitle());
        vo.setContentMarkdown(report.getContentMarkdown());
        vo.setDiaryCount(report.getDiaryCount());
        vo.setModel(report.getModel());
        vo.setCreatedAt(report.getCreatedAt());
        return vo;
    }
}
