package com.langxi.babydiary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.SyncOperationResultVO;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.SyncMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncResultStore {
    private final SyncMapper mapper;
    private final ObjectMapper objectMapper;

    public SyncResultStore(SyncMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public SyncOperationResultVO find(String operationId, Integer userId, Long spaceId) {
        String json = mapper.findOperationResult(operationId, userId, spaceId);
        return json == null ? null : read(json);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncOperationResultVO save(String operationId, Integer userId, Long spaceId,
                                      SyncOperationResultVO result) {
        try {
            mapper.insertOperationResult(operationId, userId, spaceId, write(result));
            return result;
        } catch (DuplicateKeyException duplicate) {
            SyncOperationResultVO concurrent = find(operationId, userId, spaceId);
            if (concurrent != null) return concurrent;
            throw duplicate;
        }
    }

    private String write(SyncOperationResultVO result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "同步结果序列化失败");
        }
    }

    private SyncOperationResultVO read(String json) {
        try {
            return objectMapper.readValue(json, SyncOperationResultVO.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "同步结果数据损坏");
        }
    }
}
