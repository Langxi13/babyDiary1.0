package com.langxi.babydiary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SyncOperationResultVO {
    private String operationId;
    private String status;
    private String entityId;
    private Integer version;
    private Integer errorCode;
    private String message;
}
