package com.langxi.babydiary.service;

import com.langxi.babydiary.dto.SyncOperationResultVO;
import com.langxi.babydiary.dto.SyncPullVO;
import com.langxi.babydiary.dto.SyncPushDTO;
import com.langxi.babydiary.entity.DiarySpace;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OfflineSyncService {
    private final SpaceService spaceService;
    private final SyncJournalService journalService;
    private final SyncOperationExecutor operationExecutor;

    public OfflineSyncService(SpaceService spaceService,
                              SyncJournalService journalService,
                              SyncOperationExecutor operationExecutor) {
        this.spaceService = spaceService;
        this.journalService = journalService;
        this.operationExecutor = operationExecutor;
    }

    public SyncPullVO pull(String spacePublicId, Integer userId, Long cursor, int limit) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        return journalService.pull(space.getSpaceId(), userId, cursor, limit);
    }

    public List<SyncOperationResultVO> push(String spacePublicId, Integer userId, SyncPushDTO dto,
                                            String stepUpToken) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        return dto.getOperations().stream()
                .map(operation -> operationExecutor.execute(space, userId, operation, stepUpToken))
                .toList();
    }
}
