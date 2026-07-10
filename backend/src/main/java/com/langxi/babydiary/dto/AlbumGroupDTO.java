package com.langxi.babydiary.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class AlbumGroupDTO {
    @NotBlank
    @Size(max = 100, message = "相册组名称不能超过100个字符")
    private String name;
}
