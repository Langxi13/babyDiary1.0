package com.langxi.babydiary.identity.api;

import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.AuthenticationService;
import com.langxi.babydiary.identity.application.RegistrationService;
import com.langxi.babydiary.identity.application.StepUpService;
import com.langxi.babydiary.platform.application.RequestRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/auth")
public class AuthController {
    static final String REFRESH_COOKIE = "baby_diary_refresh";

    private final AuthenticationService authentication;
    private final StepUpService stepUp;
    private final RegistrationService registrations;
    private final RequestRateLimiter rateLimiter;
    private final boolean secureCookie;
    private final int refreshDays;

    public AuthController(
            AuthenticationService authentication,
            StepUpService stepUp,
            RegistrationService registrations,
            RequestRateLimiter rateLimiter,
            @Value("${app.auth.secure-cookie:false}") boolean secureCookie,
            @Value("${app.auth.refresh-days:30}") int refreshDays) {
        this.authentication = authentication;
        this.stepUp = stepUp;
        this.registrations = registrations;
        this.rateLimiter = rateLimiter;
        this.secureCookie = secureCookie;
        this.refreshDays = refreshDays;
    }

    @PostMapping("/login")
    public ResponseEntity<SessionResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Name", required = false) String deviceName,
            HttpServletRequest servletRequest) {
        rateLimiter.require(
                "login", rateLimiter.client(servletRequest) + ":" + request.username(), 10, 900);
        AuthenticationService.Session session =
                authentication.login(
                        request.username(),
                        request.password(),
                        new AuthenticationService.Device(
                                deviceName,
                                servletRequest.getHeader(HttpHeaders.USER_AGENT),
                                servletRequest.getRemoteAddr()));
        return response(session);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        rateLimiter.require("register", rateLimiter.client(servletRequest), 5, 3600);
        registrations.register(
                request.username(),
                request.password(),
                request.confirmPassword(),
                request.invitationCode());
    }

    @PostMapping("/refresh")
    public ResponseEntity<SessionResponse> refresh(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
        return response(authentication.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
        authentication.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @PostMapping("/step-up")
    public StepUpResponse stepUp(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody PasswordRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.require(
                "step-up",
                rateLimiter.client(servletRequest) + ":" + principal.accountId(),
                10,
                900);
        StepUpService.Verified result = stepUp.verifyPassword(principal, request.password());
        return new StepUpResponse(result.token(), result.expiresAt());
    }

    @GetMapping("/sessions")
    public java.util.List<AuthenticationService.SessionView> sessions(
            @AuthenticationPrincipal AccountPrincipal principal,
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {
        return authentication.sessions(principal.accountId(), refreshToken);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID sessionId) {
        authentication.revokeSession(principal.accountId(), sessionId);
    }

    private ResponseEntity<SessionResponse> response(AuthenticationService.Session session) {
        SessionResponse body =
                new SessionResponse(
                        session.accountId(),
                        session.username(),
                        session.email(),
                        session.role(),
                        session.timezone(),
                        session.accessToken(),
                        session.accessExpiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/v3/auth")
                .maxAge(Duration.ofDays(refreshDays))
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api/v3/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

    public record LoginRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 200) String password) {}

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 200) String password,
            @NotBlank @Size(max = 200) String confirmPassword,
            @NotBlank @Size(max = 100) String invitationCode) {}

    public record SessionResponse(
            UUID accountId,
            String username,
            String email,
            String role,
            String timezone,
            String accessToken,
            long accessExpiresAt) {
        @com.fasterxml.jackson.annotation.JsonProperty("token")
        public String token() {
            return accessToken;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("userInfo")
        public UserInfo userInfo() {
            return new UserInfo(accountId, username, email, role, timezone);
        }
    }

    public record UserInfo(UUID id, String username, String email, String role, String timezone) {}

    public record PasswordRequest(@NotBlank @Size(max = 200) String password) {}

    public record StepUpResponse(String token, java.time.Instant expiresAt) {}
}
