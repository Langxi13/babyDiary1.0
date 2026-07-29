package com.langxi.babydiary.v3.identity.api;

import com.langxi.babydiary.v3.identity.application.AccountRecoveryService;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import com.langxi.babydiary.v3.media.application.MediaUrlSigner;
import com.langxi.babydiary.v3.platform.application.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v3")
public class AccountRecoveryController {
    private final AccountRecoveryService recovery;
    private final MediaUrlSigner mediaUrls;
    private final RequestRateLimiter rateLimiter;
    public AccountRecoveryController(AccountRecoveryService recovery, MediaUrlSigner mediaUrls, RequestRateLimiter rateLimiter) {
        this.recovery = recovery; this.mediaUrls = mediaUrls; this.rateLimiter = rateLimiter;
    }

    @PutMapping("/account/email")
    public EmailResponse email(@AuthenticationPrincipal V3Principal principal, @Valid @RequestBody EmailRequest request) {
        AccountRecoveryService.EmailUpdate result = recovery.updateEmail(principal.accountId(), request.email());
        return new EmailResponse(ProfileController.ProfileResponse.from(result.profile(), mediaUrls), result.mailSent());
    }
    @PostMapping("/auth/email/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody TokenRequest request, HttpServletRequest servletRequest) {
        rateLimiter.require("email-confirm", rateLimiter.client(servletRequest), 20, 3600);
        recovery.confirmEmail(request.token());
    }
    @PostMapping("/auth/password/reset-request") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestReset(@Valid @RequestBody EmailRequest request, HttpServletRequest servletRequest) {
        rateLimiter.require("reset-request", rateLimiter.client(servletRequest) + ":" + request.email(), 5, 3600);
        recovery.requestPasswordReset(request.email());
    }
    @PostMapping("/auth/password/reset") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetRequest request, HttpServletRequest servletRequest) {
        rateLimiter.require("password-reset", rateLimiter.client(servletRequest), 10, 3600);
        recovery.resetPassword(request.token(), request.newPassword());
    }
    @PostMapping("/auth/recovery-codes")
    public List<String> codes(@AuthenticationPrincipal V3Principal principal, @Valid @RequestBody PasswordRequest request) {
        return recovery.recoveryCodes(principal.accountId(), request.password());
    }
    @PostMapping("/auth/password/recover") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recover(@Valid @RequestBody RecoverRequest request, HttpServletRequest servletRequest) {
        rateLimiter.require("password-recover", rateLimiter.client(servletRequest) + ":" + request.username(), 10, 3600);
        recovery.recover(request.username(), request.recoveryCode(), request.newPassword());
    }

    public record EmailRequest(@NotBlank @Size(max=255) String email) {}
    public record TokenRequest(@NotBlank @Size(max=200) String token) {}
    public record ResetRequest(@NotBlank @Size(max=200) String token, @NotBlank @Size(min=8,max=200) String newPassword) {}
    public record PasswordRequest(@NotBlank @Size(max=200) String password) {}
    public record RecoverRequest(@NotBlank @Size(max=100) String username, @NotBlank @Size(max=100) String recoveryCode,
                                 @NotBlank @Size(min=8,max=200) String newPassword) {}
    public record EmailResponse(ProfileController.ProfileResponse profile, boolean mailSent) {}
}
