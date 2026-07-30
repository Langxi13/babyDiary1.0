package com.langxi.babydiary.v3.identity.application;

import com.langxi.babydiary.v3.platform.application.V3Exception;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class StepUpService {
    private final ProfileRepository profiles;
    private final PasswordEncoder passwords;
    private final SecretKey key;
    private final Clock clock = Clock.systemUTC();

    public StepUpService(ProfileRepository profiles, PasswordEncoder passwords, @Value("${jwt.secret}") String secret) {
        this.profiles = profiles;
        this.passwords = passwords;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Verified verifyPassword(V3Principal principal, String password) {
        ProfileRepository.Profile profile = requireProfile(principal);
        if (password == null || !passwords.matches(password, profile.passwordHash())) {
            throw new V3Exception(org.springframework.http.HttpStatus.UNAUTHORIZED, "STEP_UP_INVALID", "二次验证失败");
        }
        Instant expiresAt = clock.instant().plusSeconds(300);
        String token = Jwts.builder().subject(profile.id().toString()).claim("purpose", "step-up")
                .claim("aid", profile.accountId()).issuedAt(Date.from(clock.instant())).expiration(Date.from(expiresAt))
                .signWith(key).compact();
        return new Verified(token, expiresAt);
    }

    public void require(V3Principal principal, String token) {
        if (principal == null || token == null || token.isBlank()) throw invalid();
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Number accountId = claims.get("aid", Number.class);
            if (!"step-up".equals(claims.get("purpose", String.class)) || accountId == null
                    || accountId.longValue() != principal.accountId() || claims.getExpiration().toInstant().isBefore(clock.instant())) {
                throw invalid();
            }
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    public boolean valid(V3Principal principal, String token) {
        try {
            require(principal, token);
            return true;
        } catch (V3Exception exception) {
            return false;
        }
    }

    private ProfileRepository.Profile requireProfile(V3Principal principal) {
        if (principal == null) throw invalid();
        return profiles.find(principal.accountId())
                .orElseThrow(() -> V3Exception.notFound("ACCOUNT_NOT_FOUND", "账户不存在"));
    }

    private V3Exception invalid() {
        return new V3Exception(org.springframework.http.HttpStatus.LOCKED, "STEP_UP_REQUIRED", "请先完成二次验证");
    }

    public record Verified(String token, Instant expiresAt) {
    }
}
