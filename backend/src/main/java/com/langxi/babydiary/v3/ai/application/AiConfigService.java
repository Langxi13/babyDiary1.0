package com.langxi.babydiary.v3.ai.application;

import com.langxi.babydiary.util.AesGcmSecretCodec;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;

@Service
public class AiConfigService {
    private final AiConfigRepository configs;
    private final V3AiClient client;
    private final AesGcmSecretCodec secrets;

    public AiConfigService(AiConfigRepository configs, V3AiClient client,
                           @Value("${ai.config.encryption-key}") String encryptionKey) {
        this.configs = configs;
        this.client = client;
        this.secrets = new AesGcmSecretCodec(encryptionKey);
    }

    public ConfigView view(V3Principal principal) {
        requireAdmin(principal);
        AiConfigRepository.Config config = configs.find();
        if (config == null) return new ConfigView(false, null, null, false, 30, null);
        return new ConfigView(config.enabled(), config.baseUrl(), config.model(),
                config.encryptedApiKey() != null && !config.encryptedApiKey().isBlank(), config.timeoutSeconds(), config.updatedAt());
    }

    @Transactional
    public ConfigView save(V3Principal principal, Command command) {
        requireAdmin(principal);
        String baseUrl = normalizeBaseUrl(command.baseUrl());
        String model = blankToNull(command.model());
        int timeout = Math.max(5, Math.min(command.timeoutSeconds() <= 0 ? 30 : command.timeoutSeconds(), 300));
        AiConfigRepository.Config existing = configs.find();
        String encrypted = blankToNull(command.apiKey()) == null
                ? existing == null ? null : existing.encryptedApiKey()
                : secrets.encrypt(command.apiKey().trim());
        if (command.enabled() && (baseUrl == null || model == null || encrypted == null)) {
            throw V3Exception.badRequest("AI_CONFIG_INCOMPLETE", "启用 AI 前请完整配置地址、模型和 API Key");
        }
        configs.upsert(new AiConfigRepository.NewConfig(command.enabled(), baseUrl, model, encrypted, timeout,
                principal.accountId()));
        return view(principal);
    }

    public String test(V3Principal principal) {
        requireAdmin(principal);
        return client.generate(runtime(), java.util.List.of(new V3AiClient.Message("user", "请只回复 OK，用于测试连接。")));
    }

    public java.util.List<String> models(V3Principal principal) {
        requireAdmin(principal);
        return client.listModels(runtime());
    }

    public AiRuntimeConfig runtime() {
        AiConfigRepository.Config config = configs.find();
        if (config == null || !config.enabled() || config.baseUrl() == null || config.model() == null
                || config.encryptedApiKey() == null) {
            throw V3Exception.badRequest("AI_CONFIG_INCOMPLETE", "AI 配置未启用或不完整");
        }
        try {
            return new AiRuntimeConfig(config.baseUrl(), secrets.decrypt(config.encryptedApiKey()), config.model(), config.timeoutSeconds());
        } catch (RuntimeException exception) {
            throw V3Exception.badRequest("AI_SECRET_INVALID", "AI 密钥无法解密，请重新配置");
        }
    }

    private void requireAdmin(V3Principal principal) {
        if (principal == null || !"ADMIN".equals(principal.role())) {
            throw V3Exception.forbidden("ADMIN_REQUIRED", "只有管理员可以管理 AI 配置");
        }
    }

    private String normalizeBaseUrl(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        try {
            URI uri = new URI(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
            return normalized;
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw V3Exception.badRequest("AI_BASE_URL_INVALID", "AI 地址必须是合法的 HTTP 或 HTTPS 地址");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public record Command(boolean enabled, String baseUrl, String model, String apiKey, int timeoutSeconds) {
    }

    public record ConfigView(boolean enabled, String baseUrl, String model, boolean hasApiKey,
                             int timeoutSeconds, java.time.LocalDateTime updatedAt) {
    }
}
