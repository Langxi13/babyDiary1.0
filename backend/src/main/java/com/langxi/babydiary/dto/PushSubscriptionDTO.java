package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PushSubscriptionDTO {
    @NotBlank(message = "推送地址不能为空")
    @Size(max = 4096, message = "推送地址过长")
    private String endpoint;

    @NotBlank(message = "推送公钥不能为空")
    @Size(max = 255, message = "推送公钥过长")
    private String p256dh;

    @NotBlank(message = "推送认证信息不能为空")
    @Size(max = 255, message = "推送认证信息过长")
    private String auth;
}
