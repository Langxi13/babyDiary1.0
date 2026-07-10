package com.langxi.babydiary.dto;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class AlbumPhotoDTO {
    @NotEmpty(message = "请选择照片")
    @Size(max = 500, message = "单次最多添加500张照片")
    private List<@NotNull @Positive Integer> imageIds;
}
