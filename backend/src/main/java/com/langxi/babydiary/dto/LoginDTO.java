package com.langxi.babydiary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "登录请求")
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 255, message = "用户名长度无效")
    @Schema(description = "用户名", example = "langxi", required = true)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度无效")
    @Schema(description = "密码", example = "123456", required = true)
    private String password;
}
