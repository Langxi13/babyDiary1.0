package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.AiConfigDTO;
import com.langxi.babydiary.dto.AiConfigVO;
import com.langxi.babydiary.entity.AiConfig;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AiConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

@Service
public class AiConfigService {

    @Autowired
    private AiConfigMapper aiConfigMapper;

    @Autowired
    private AiConfigCrypto aiConfigCrypto;

    @Autowired
    private OpenAiCompatibleClient aiClient;

    public AiConfigVO getConfig() {
        return AiConfigVO.fromEntity(aiConfigMapper.findConfig());
    }

    public AiConfigVO saveConfig(AiConfigDTO dto) {
        AiConfig existing = aiConfigMapper.findConfig();
        AiConfig config = new AiConfig();
        config.setConfigId(1);
        config.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        config.setBaseUrl(normalizeBaseUrl(dto.getBaseUrl()));
        config.setModel(trimToNull(dto.getModel()));
        config.setTimeoutSeconds(normalizeTimeout(dto.getTimeoutSeconds()));
        if (dto.getApiKey() != null && !dto.getApiKey().trim().isEmpty()) {
            config.setEncryptedApiKey(aiConfigCrypto.encrypt(dto.getApiKey().trim()));
        } else if (existing != null) {
            config.setEncryptedApiKey(existing.getEncryptedApiKey());
        }
        aiConfigMapper.upsertConfig(config);
        return getConfig();
    }

    public AiRuntimeConfig getRuntimeConfig() {
        AiConfig config = aiConfigMapper.findConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(ErrorCode.AI_CONFIG_INVALID, "AI配置未启用");
        }
        if (isBlank(config.getBaseUrl()) || isBlank(config.getModel()) || isBlank(config.getEncryptedApiKey())) {
            throw new BusinessException(ErrorCode.AI_CONFIG_INVALID, "AI配置不完整");
        }
        return new AiRuntimeConfig(
                config.getBaseUrl().trim(),
                aiConfigCrypto.decrypt(config.getEncryptedApiKey()),
                config.getModel().trim(),
                normalizeTimeout(config.getTimeoutSeconds())
        );
    }

    public String testConnection() {
        return aiClient.generate(getRuntimeConfig(), Collections.singletonList(
                new AiChatMessage("user", "请只回复 OK，用于测试连接。")
        ));
    }

    public List<String> listModels() {
        AiConfig config = aiConfigMapper.findConfig();
        if (config == null || isBlank(config.getBaseUrl()) || isBlank(config.getEncryptedApiKey())) {
            throw new BusinessException(ErrorCode.AI_CONFIG_INVALID, "请先保存Base URL和API Key");
        }
        return aiClient.listModels(new AiRuntimeConfig(
                config.getBaseUrl().trim(),
                aiConfigCrypto.decrypt(config.getEncryptedApiKey()),
                config.getModel() == null ? "" : config.getModel().trim(),
                normalizeTimeout(config.getTimeoutSeconds())
        ));
    }

    private Integer normalizeTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null) {
            return 30;
        }
        return Math.max(5, Math.min(timeoutSeconds, 120));
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = trimToNull(baseUrl);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw invalidBaseUrl("Base URL 仅支持 HTTP 或 HTTPS");
            }
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw invalidBaseUrl("Base URL 不能包含账号、查询参数或片段");
            }
            String result = normalized;
            while (result.endsWith("/")) {
                result = result.substring(0, result.length() - 1);
            }
            return result;
        } catch (URISyntaxException e) {
            throw invalidBaseUrl("Base URL 格式无效");
        }
    }

    private BusinessException invalidBaseUrl(String message) {
        return new BusinessException(ErrorCode.AI_CONFIG_INVALID, message);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
