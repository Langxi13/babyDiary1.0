package com.langxi.babydiary.identity.api;

import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.InvitationCodeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/admin/invitation-code")
public class InvitationCodeController {
    private final InvitationCodeService codes;

    public InvitationCodeController(InvitationCodeService codes) {
        this.codes = codes;
    }

    @PostMapping("/view")
    public CodeResponse view(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        return new CodeResponse(codes.view(principal, token));
    }

    @PostMapping("/rotate")
    public CodeResponse rotate(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String token) {
        return new CodeResponse(codes.rotate(principal, token));
    }

    public record CodeResponse(String code) {}
}
