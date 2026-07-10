package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.AiConfig;
import lombok.Data;

@Data
public class AiConfigVO {
    private Boolean enabled;
    private String baseUrl;
    private String model;
    private String apiKeyMasked;
    private Integer timeoutSeconds;
    private Boolean configured;

    public static AiConfigVO fromEntity(AiConfig config) {
        AiConfigVO vo = new AiConfigVO();
        if (config == null) {
            vo.setEnabled(false);
            vo.setTimeoutSeconds(30);
            vo.setConfigured(false);
            return vo;
        }
        vo.setEnabled(Boolean.TRUE.equals(config.getEnabled()));
        vo.setBaseUrl(config.getBaseUrl());
        vo.setModel(config.getModel());
        vo.setTimeoutSeconds(config.getTimeoutSeconds() == null ? 30 : config.getTimeoutSeconds());
        vo.setConfigured(config.getEncryptedApiKey() != null && !config.getEncryptedApiKey().trim().isEmpty());
        vo.setApiKeyMasked(Boolean.TRUE.equals(vo.getConfigured()) ? "********" : "");
        return vo;
    }
}
