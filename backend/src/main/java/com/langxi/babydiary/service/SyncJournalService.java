package com.langxi.babydiary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.DiaryVO;
import com.langxi.babydiary.dto.SyncChangeVO;
import com.langxi.babydiary.dto.SyncPullVO;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.SyncMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SyncJournalService {
    private final SyncMapper mapper;
    private final ObjectMapper objectMapper;

    public SyncJournalService(SyncMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void recordDiary(Long spaceId, Integer actorUserId, String operation, DiaryVO diary) {
        mapper.insertChange(spaceId, "DIARY", diary.getPublicId(), operation, diary.getVersion(), actorUserId, json(diary));
    }

    public void recordDiaryDeletion(Long spaceId, Integer actorUserId, String publicId, Integer version) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("publicId", publicId);
        payload.put("version", version);
        payload.put("deleted", true);
        mapper.insertChange(spaceId, "DIARY", publicId, "DELETE", version, actorUserId, json(payload));
    }

    public SyncPullVO pull(Long spaceId, Integer userId, Long cursor, int limit) {
        long normalizedCursor = cursor == null || cursor < 0 ? 0 : cursor;
        int normalizedLimit = Math.max(1, Math.min(limit, 500));
        List<SyncChangeVO> changes = mapper.findChanges(spaceId, userId, normalizedCursor, normalizedLimit + 1);
        boolean hasMore = changes.size() > normalizedLimit;
        if (hasMore) changes = changes.subList(0, normalizedLimit);
        long nextCursor = changes.isEmpty() ? normalizedCursor : changes.get(changes.size() - 1).getCursor();
        return new SyncPullVO(nextCursor, hasMore, changes);
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "同步数据序列化失败");
        }
    }
}
