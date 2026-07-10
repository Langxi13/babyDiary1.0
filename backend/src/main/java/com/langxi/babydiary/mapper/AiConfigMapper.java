package com.langxi.babydiary.mapper;

import com.langxi.babydiary.entity.AiConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiConfigMapper {
    AiConfig findConfig();

    void upsertConfig(AiConfig config);
}
