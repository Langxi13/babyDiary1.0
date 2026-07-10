package com.langxi.babydiary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Schema(description = "修改密码请求")
public class PasswordChangeDTO {

    @NotBlank(message = "旧密码不能为空")
    @Size(max = 128, message = "旧密码长度无效")
    @Schema(description = "旧密码")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需在6到64位之间")
    @Schema(description = "新密码")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    @Size(min = 6, max = 64, message = "确认密码长度需在6到64位之间")
    @Schema(description = "确认密码")
    private String confirmPassword;
}
