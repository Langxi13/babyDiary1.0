package com.langxi.babydiary.identity.application;

import com.langxi.babydiary.platform.application.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class StepUpService {
    private final ProfileRepository profiles;
    private final CredentialRepository credentials;
    private final PasswordEncoder passwords;
    private final SecretKey key;
    private final Clock clock = Clock.systemUTC();

    public StepUpService(
            ProfileRepository profiles,
            CredentialRepository credentials,
            PasswordEncoder passwords,
            @Value("${jwt.secret}") String secret) {
        this.profiles = profiles;
        this.credentials = credentials;
        this.passwords = passwords;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Verified verifyPassword(AccountPrincipal principal, String password) {
        ProfileRepository.Profile profile = requireProfile(principal);
        String passwordHash = credentials.findPasswordHash(profile.accountId()).orElse(null);
        if (password == null
                || passwordHash == null
                || !passwords.matches(password, passwordHash)) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "STEP_UP_INVALID", "二次验证失败");
        }
        Instant expiresAt = clock.instant().plusSeconds(300);
        String token =
                Jwts.builder()
                        .subject(profile.id().toString())
                        .claim("purpose", "step-up")
                        .claim("aid", profile.accountId())
                        .issuedAt(Date.from(clock.instant()))
                        .expiration(Date.from(expiresAt))
                        .signWith(key)
                        .compact();
        return new Verified(token, expiresAt);
    }

    public void require(AccountPrincipal principal, String token) {
        if (principal == null || token == null || token.isBlank()) throw invalid();
        try {
            Claims claims =
                    Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Number accountId = claims.get("aid", Number.class);
            if (!"step-up".equals(claims.get("purpose", String.class))
                    || accountId == null
                    || accountId.longValue() != principal.accountId()
                    || claims.getExpiration().toInstant().isBefore(clock.instant())) {
                throw invalid();
            }
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    public boolean valid(AccountPrincipal principal, String token) {
        try {
            require(principal, token);
            return true;
        } catch (ApiException exception) {
            return false;
        }
    }

    private ProfileRepository.Profile requireProfile(AccountPrincipal principal) {
        if (principal == null) throw invalid();
        return profiles.find(principal.accountId())
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "账户不存在"));
    }

    private ApiException invalid() {
        return new ApiException(
                org.springframework.http.HttpStatus.LOCKED, "STEP_UP_REQUIRED", "请先完成二次验证");
    }

    public record Verified(String token, Instant expiresAt) {}
}
