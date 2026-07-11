package com.langxi.babydiary.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SpaceInviteDTO {
    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "OWNER|MEMBER", message = "成员角色无效")
    private String role = "MEMBER";
}
