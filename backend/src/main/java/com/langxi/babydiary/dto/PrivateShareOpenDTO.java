package com.langxi.babydiary.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PrivateShareOpenDTO {
    @Size(max = 64, message = "分享密码长度无效")
    private String password;
}
