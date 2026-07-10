package com.langxi.babydiary.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.sql.Date;

@Data
@AllArgsConstructor
public class AiReportPeriod {
    private String type;
    private String label;
    private Date startDate;
    private Date endDate;
}
