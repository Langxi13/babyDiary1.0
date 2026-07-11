package com.langxi.babydiary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SyncOperationDTO {
    @NotBlank(message = "同步操作ID不能为空")
    @Size(max = 36, message = "同步操作ID长度无效")
    private String operationId;

    @NotBlank(message = "同步实体类型不能为空")
    @Pattern(regexp = "DIARY", message = "暂不支持该同步实体")
    private String entityType;

    @NotBlank(message = "同步动作不能为空")
    @Pattern(regexp = "CREATE|UPDATE|DELETE|RESTORE", message = "同步动作无效")
    private String action;

    @Size(max = 36, message = "实体ID长度无效")
    private String entityId;

    private Integer baseVersion;

    @Valid
    private DiaryWriteDTO payload;
}
