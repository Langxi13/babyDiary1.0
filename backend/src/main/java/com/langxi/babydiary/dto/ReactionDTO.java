package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReactionDTO {
    @NotBlank(message = "回应不能为空")
    @Size(max = 16, message = "回应内容过长")
    private String emoji;

    private boolean active = true;
}
