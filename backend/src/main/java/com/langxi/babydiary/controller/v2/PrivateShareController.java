package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.PrivateShareCreateDTO;
import com.langxi.babydiary.dto.PrivateShareOpenDTO;
import com.langxi.babydiary.dto.PrivateShareSummaryVO;
import com.langxi.babydiary.dto.PrivateShareVO;
import com.langxi.babydiary.dto.SharedDiaryVO;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.PrivateShareService;
import com.langxi.babydiary.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class PrivateShareController {
    private final PrivateShareService shareService;
    private final CurrentUser currentUser;
    private final RateLimitService rateLimitService;

    public PrivateShareController(PrivateShareService shareService,
                                  CurrentUser currentUser,
                                  RateLimitService rateLimitService) {
        this.shareService = shareService;
        this.currentUser = currentUser;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/api/v2/spaces/{spaceId}/diaries/{diaryId}/shares")
    public Result<PrivateShareVO> create(@PathVariable String spaceId,
                                         @PathVariable String diaryId,
                                         @Valid @RequestBody PrivateShareCreateDTO dto,
                                         @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        return Result.success("私密分享已创建", shareService.create(
                spaceId, diaryId, currentUser.getUserId(), dto, stepUpToken));
    }

    @GetMapping("/api/v2/spaces/{spaceId}/diaries/{diaryId}/shares")
    public Result<List<PrivateShareSummaryVO>> list(@PathVariable String spaceId,
                                                     @PathVariable String diaryId,
                                                     @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        return Result.success(shareService.list(spaceId, diaryId, currentUser.getUserId(), stepUpToken));
    }

    @DeleteMapping("/api/v2/shares/{shareId}")
    public Result<Void> revoke(@PathVariable String shareId) {
        shareService.revoke(shareId, currentUser.getUserId());
        return Result.success("分享已撤销", null);
    }

    @PostMapping("/api/v2/public/shares/{token}/open")
    public Result<SharedDiaryVO> open(@PathVariable String token,
                                      @Valid @RequestBody(required = false) PrivateShareOpenDTO dto,
                                      HttpServletRequest request) {
        rateLimitService.require("private-share", rateLimitService.clientAddress(request) + ":" + token, 20, Duration.ofMinutes(15));
        return Result.success(shareService.open(token, dto == null ? null : dto.getPassword()));
    }
}
