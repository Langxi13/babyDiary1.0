package com.langxi.babydiary.v3.ai.infrastructure;

import com.langxi.babydiary.v3.ai.application.AiConfigRepository;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAiConfigRepository implements AiConfigRepository {
    private final AiConfigMapper mapper;

    public MyBatisAiConfigRepository(AiConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Config find() {
        AiConfigMapper.ConfigRow row = mapper.find();
        return row == null ? null : new Config(row.enabled(), row.baseUrl(), row.model(), row.encryptedApiKey(),
                row.timeoutSeconds(), row.updatedAt());
    }

    @Override
    public void upsert(NewConfig config) {
        mapper.upsert(config);
    }
}
