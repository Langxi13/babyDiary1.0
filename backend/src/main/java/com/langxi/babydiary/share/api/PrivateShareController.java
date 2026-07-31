package com.langxi.babydiary.share.api;

import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.platform.api.ApiContract;
import com.langxi.babydiary.share.application.PrivateShareService;
import com.langxi.babydiary.share.application.PublicShareRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class PrivateShareController {
    private final PrivateShareService shares;
    private final PublicShareRateLimiter limiter;

    public PrivateShareController(PrivateShareService shares, PublicShareRateLimiter limiter) {
        this.shares = shares;
        this.limiter = limiter;
    }

    @PostMapping(ApiContract.ROOT + "/spaces/{spaceId}/diaries/{diaryId}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public PrivateShareService.Created create(
            @AuthenticationPrincipal AccountPrincipal p,
            @PathVariable UUID spaceId,
            @PathVariable UUID diaryId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String step,
            @Valid @RequestBody CreateRequest r) {
        return shares.create(spaceId, diaryId, p, step, r.expiresInHours, r.password, r.maxViews);
    }

    @GetMapping(ApiContract.ROOT + "/spaces/{spaceId}/diaries/{diaryId}/shares")
    public List<PrivateShareService.Summary> list(
            @AuthenticationPrincipal AccountPrincipal p,
            @PathVariable UUID spaceId,
            @PathVariable UUID diaryId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String step) {
        return shares.list(spaceId, diaryId, p, step);
    }

    @DeleteMapping(ApiContract.ROOT + "/shares/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AccountPrincipal p, @PathVariable UUID shareId) {
        shares.revoke(shareId, p.accountId());
    }

    @PostMapping(ApiContract.ROOT + "/public/shares/{token}/open")
    public PrivateShareService.SharedDiary open(
            @PathVariable String token,
            @RequestBody(required = false) OpenRequest r,
            HttpServletRequest request) {
        limiter.require(request.getRemoteAddr() + ":" + token);
        return shares.open(token, r == null ? null : r.password);
    }

    public record CreateRequest(
            @Min(1) @Max(720) int expiresInHours,
            @Size(min = 4, max = 64) String password,
            @Min(1) @Max(10000) Integer maxViews) {}

    public record OpenRequest(String password) {}
}
