package com.langxi.babydiary.ai.infrastructure;

import com.langxi.babydiary.ai.application.AiConfigRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface AiConfigMapper {
    @Select("SELECT enabled,base_url,model,encrypted_api_key,timeout_seconds,updated_at FROM ai_config WHERE config_id=1")
    ConfigRow find();

    @Insert("""
            INSERT INTO ai_config(config_id,enabled,base_url,model,encrypted_api_key,timeout_seconds,updated_by,updated_at)
            VALUES(1,#{enabled},#{baseUrl},#{model},#{encryptedApiKey},#{timeoutSeconds},#{updatedBy},UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE enabled=VALUES(enabled),base_url=VALUES(base_url),model=VALUES(model),
              encrypted_api_key=COALESCE(VALUES(encrypted_api_key),encrypted_api_key),timeout_seconds=VALUES(timeout_seconds),
              updated_by=VALUES(updated_by),updated_at=UTC_TIMESTAMP(6)
            """)
    void upsert(AiConfigRepository.NewConfig config);

    final class ConfigRow {
        private boolean enabled;
        private String baseUrl;
        private String model;
        private String encryptedApiKey;
        private int timeoutSeconds;
        private LocalDateTime updatedAt;

        public ConfigRow() {
        }

        public boolean enabled() { return enabled; }
        public String baseUrl() { return baseUrl; }
        public String model() { return model; }
        public String encryptedApiKey() { return encryptedApiKey; }
        public int timeoutSeconds() { return timeoutSeconds; }
        public LocalDateTime updatedAt() { return updatedAt; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public void setModel(String model) { this.model = model; }
        public void setEncryptedApiKey(String encryptedApiKey) { this.encryptedApiKey = encryptedApiKey; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
