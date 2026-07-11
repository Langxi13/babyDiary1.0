package com.langxi.babydiary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Schema(description = "用户视图对象")
public class UserVO {

    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "已验证邮箱")
    private String email;

    @Schema(description = "邮箱是否已验证")
    private Boolean emailVerified;

    @Schema(description = "系统角色")
    private String systemRole;

    @Schema(description = "用户时区")
    private String timezone;

    @Schema(description = "头像路径")
    private String avatarPath;

    @Schema(description = "创建时间")
    private Timestamp createdAt;

    public static UserVO fromEntity(com.langxi.babydiary.entity.User user) {
        UserVO vo = new UserVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setEmail(user.getEmail());
        vo.setEmailVerified(user.getEmailVerified());
        vo.setSystemRole(user.getSystemRole());
        vo.setTimezone(user.getTimezone());
        vo.setAvatarPath(user.getAvatarPath());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
