package com.langxi.babydiary.v3.ai.application;

import java.time.LocalDateTime;

public interface AiConfigRepository {
    Config find();

    void upsert(NewConfig config);

    record Config(boolean enabled, String baseUrl, String model, String encryptedApiKey,
                  int timeoutSeconds, LocalDateTime updatedAt) {
    }

    record NewConfig(boolean enabled, String baseUrl, String model, String encryptedApiKey,
                     int timeoutSeconds, long updatedBy) {
    }
}
