package com.langxi.babydiary.common;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    RATE_LIMITED(429, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户名已存在"),
    PASSWORD_MISMATCH(1003, "密码不匹配"),
    INVALID_INVITATION_CODE(1004, "邀请码无效"),
    LOGIN_FAILED(1005, "登录失败，用户名或密码错误"),
    TOKEN_EXPIRED(1006, "Token已过期"),
    TOKEN_INVALID(1007, "Token无效"),
    EMAIL_ALREADY_EXISTS(1008, "邮箱已被使用"),
    RECOVERY_TOKEN_INVALID(1009, "找回凭证无效或已过期"),

    SPACE_NOT_FOUND(1501, "空间不存在"),
    SPACE_ACCESS_DENIED(1502, "无权访问该空间"),
    SPACE_OWNER_REQUIRED(1503, "需要空间所有者权限"),
    SPACE_INVITATION_INVALID(1504, "邀请无效或已过期"),
    SPACE_LAST_OWNER(1505, "空间必须至少保留一名所有者"),

    DIARY_NOT_FOUND(2001, "日记不存在"),
    DIARY_CREATE_FAILED(2002, "创建日记失败"),
    DIARY_UPDATE_FAILED(2003, "更新日记失败"),
    DIARY_DELETE_FAILED(2004, "删除日记失败"),
    DIARY_VERSION_CONFLICT(2005, "日记已被其他成员修改"),
    DIARY_LOCKED(2006, "日记需要重新验证后访问"),

    FILE_UPLOAD_FAILED(3001, "文件上传失败"),
    FILE_TYPE_NOT_ALLOWED(3002, "文件类型不支持"),
    FILE_SIZE_EXCEEDED(3003, "文件大小超出限制"),
    FILE_NOT_FOUND(3004, "文件不存在"),

    AI_CONFIG_INVALID(4001, "AI配置无效"),
    AI_REQUEST_FAILED(4002, "AI请求失败"),
    AI_REPORT_NOT_FOUND(4003, "AI报告不存在"),
    ALBUM_NOT_FOUND(5001, "相册不存在"),
    ALBUM_GROUP_NOT_FOUND(5002, "相册组不存在"),
    AI_ALBUM_PROPOSAL_NOT_FOUND(5003, "AI相册提案不存在");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
