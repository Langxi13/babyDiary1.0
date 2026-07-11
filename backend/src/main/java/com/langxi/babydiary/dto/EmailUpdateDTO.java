package com.langxi.babydiary.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailUpdateDTO {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式无效")
    @Size(max = 255, message = "邮箱长度无效")
    private String email;
}
