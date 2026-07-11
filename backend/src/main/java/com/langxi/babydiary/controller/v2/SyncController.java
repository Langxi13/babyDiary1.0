package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.SyncOperationResultVO;
import com.langxi.babydiary.dto.SyncPullVO;
import com.langxi.babydiary.dto.SyncPushDTO;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.OfflineSyncService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/spaces/{spaceId}/sync")
public class SyncController {
    private final OfflineSyncService syncService;
    private final CurrentUser currentUser;

    public SyncController(OfflineSyncService syncService, CurrentUser currentUser) {
        this.syncService = syncService;
        this.currentUser = currentUser;
    }

    @GetMapping("/pull")
    public Result<SyncPullVO> pull(@PathVariable String spaceId,
                                   @RequestParam(defaultValue = "0") Long cursor,
                                   @RequestParam(defaultValue = "200") int limit) {
        return Result.success(syncService.pull(spaceId, currentUser.getUserId(), cursor, limit));
    }

    @PostMapping("/push")
    public Result<List<SyncOperationResultVO>> push(@PathVariable String spaceId,
                                                    @Valid @RequestBody SyncPushDTO dto,
                                                    @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        return Result.success(syncService.push(spaceId, currentUser.getUserId(), dto, stepUpToken));
    }
}
