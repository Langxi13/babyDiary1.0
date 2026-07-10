package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TagCreateDTO {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 32, message = "标签名不能超过32个字符")
    private String name;

    @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$", message = "标签颜色格式无效")
    private String color;
}
