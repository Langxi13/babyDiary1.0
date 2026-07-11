package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSpaceDTO {
    @NotBlank(message = "空间名称不能为空")
    @Size(max = 100, message = "空间名称不能超过100个字符")
    private String name;
}
