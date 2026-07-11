package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PushUnsubscribeDTO {
    @NotBlank(message = "推送地址不能为空")
    @Size(max = 4096, message = "推送地址过长")
    private String endpoint;
}
