package com.langxi.babydiary.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
public class AlbumDTO {
    @NotNull
    @Positive(message = "相册组ID无效")
    private Integer groupId;

    @NotBlank
    @Size(max = 100, message = "相册名称不能超过100个字符")
    private String name;

    @Size(max = 5000, message = "相册说明不能超过5000个字符")
    private String description;

    @Size(max = 255, message = "封面路径长度无效")
    private String coverImagePath;
}
