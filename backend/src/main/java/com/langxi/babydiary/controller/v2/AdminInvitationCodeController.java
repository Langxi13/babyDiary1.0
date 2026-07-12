package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.InvitationCodeVO;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.InvitationCodeService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/invitation-code")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInvitationCodeController {
    private final InvitationCodeService invitationCodeService;
    private final CurrentUser currentUser;

    public AdminInvitationCodeController(InvitationCodeService invitationCodeService, CurrentUser currentUser) {
        this.invitationCodeService = invitationCodeService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<Result<InvitationCodeVO>> getInvitationCode(
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        InvitationCodeVO invitationCode = invitationCodeService.getVisibleCode(currentUser.getUserId(), stepUpToken);
        return noStore(Result.success(invitationCode));
    }

    @PostMapping("/rotate")
    public ResponseEntity<Result<InvitationCodeVO>> rotateInvitationCode(
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) {
        InvitationCodeVO invitationCode = invitationCodeService.rotate(currentUser.getUserId(), stepUpToken);
        return noStore(Result.success("邀请码已刷新，旧邀请码已失效", invitationCode));
    }

    private ResponseEntity<Result<InvitationCodeVO>> noStore(Result<InvitationCodeVO> result) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(result);
    }
}
