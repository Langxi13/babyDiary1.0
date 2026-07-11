package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.*;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class SyncOperationExecutor {
    private final CollaborativeDiaryService diaryService;
    private final SyncResultStore resultStore;

    public SyncOperationExecutor(CollaborativeDiaryService diaryService,
                                 SyncResultStore resultStore) {
        this.diaryService = diaryService;
        this.resultStore = resultStore;
    }

    public SyncOperationResultVO execute(DiarySpace space, Integer userId, SyncOperationDTO operation,
                                         String stepUpToken) {
        SyncOperationResultVO previous = resultStore.find(operation.getOperationId(), userId, space.getSpaceId());
        if (previous != null) return previous;

        SyncOperationResultVO result;
        try {
            result = apply(space.getPublicId(), userId, operation, stepUpToken);
        } catch (BusinessException exception) {
            String status = exception.getCode().equals(ErrorCode.DIARY_VERSION_CONFLICT.getCode()) ? "CONFLICT" : "FAILED";
            result = new SyncOperationResultVO(operation.getOperationId(), status, operation.getEntityId(),
                    operation.getBaseVersion(), exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            return new SyncOperationResultVO(operation.getOperationId(), "RETRYABLE", operation.getEntityId(),
                    operation.getBaseVersion(), ErrorCode.INTERNAL_ERROR.getCode(), "同步操作执行失败");
        }
        return resultStore.save(operation.getOperationId(), userId, space.getSpaceId(), result);
    }

    private SyncOperationResultVO apply(String spaceId, Integer userId, SyncOperationDTO operation,
                                        String stepUpToken) {
        return switch (operation.getAction()) {
            case "CREATE" -> {
                if (operation.getPayload() == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "创建日记缺少内容");
                if (operation.getEntityId() != null) operation.getPayload().setClientId(operation.getEntityId());
                DiaryVO diary = diaryService.create(spaceId, userId, operation.getPayload(), stepUpToken);
                yield success(operation, diary);
            }
            case "UPDATE" -> {
                requireEntityAndPayload(operation);
                DiaryVO diary = diaryService.update(spaceId, operation.getEntityId(), userId, operation.getPayload(),
                        operation.getBaseVersion(), stepUpToken);
                yield success(operation, diary);
            }
            case "DELETE" -> {
                requireEntity(operation);
                diaryService.moveToTrash(spaceId, operation.getEntityId(), userId, operation.getBaseVersion(), stepUpToken);
                yield new SyncOperationResultVO(operation.getOperationId(), "APPLIED", operation.getEntityId(),
                        operation.getBaseVersion() == null ? null : operation.getBaseVersion() + 1, null, null);
            }
            case "RESTORE" -> {
                requireEntity(operation);
                DiaryVO diary = diaryService.restore(spaceId, operation.getEntityId(), userId,
                        operation.getBaseVersion(), stepUpToken);
                yield success(operation, diary);
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "同步动作无效");
        };
    }

    private SyncOperationResultVO success(SyncOperationDTO operation, DiaryVO diary) {
        return new SyncOperationResultVO(operation.getOperationId(), "APPLIED", diary.getPublicId(),
                diary.getVersion(), null, null);
    }

    private void requireEntity(SyncOperationDTO operation) {
        if (operation.getEntityId() == null || operation.getEntityId().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "同步操作缺少实体ID");
        }
    }

    private void requireEntityAndPayload(SyncOperationDTO operation) {
        requireEntity(operation);
        if (operation.getPayload() == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "同步操作缺少内容");
    }

}
