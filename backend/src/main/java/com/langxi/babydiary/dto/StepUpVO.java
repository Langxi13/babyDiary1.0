package com.langxi.babydiary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
public class StepUpVO {
    private String token;
    private Timestamp expiresAt;
}
