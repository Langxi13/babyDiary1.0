package com.langxi.babydiary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SyncPushDTO {
    @NotEmpty(message = "同步操作不能为空")
    @Size(max = 100, message = "单次最多同步100个操作")
    private List<@Valid SyncOperationDTO> operations;
}
