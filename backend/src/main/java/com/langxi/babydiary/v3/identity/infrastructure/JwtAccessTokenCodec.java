package com.langxi.babydiary.v3.identity.infrastructure;

import com.langxi.babydiary.v3.identity.application.AccessTokenCodec;
import com.langxi.babydiary.v3.identity.domain.Account;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtAccessTokenCodec implements AccessTokenCodec {
    private final String secret;
    private final long expirationMillis;
    private final Clock clock;
    private SecretKey key;

    @Autowired
    public JwtAccessTokenCodec(@Value("${jwt.secret}") String secret,
                               @Value("${jwt.access-expiration:900000}") long expirationMillis) {
        this(secret, expirationMillis, Clock.systemUTC());
    }

    JwtAccessTokenCodec(String secret, long expirationMillis, Clock clock) {
        this.secret = secret;
        this.expirationMillis = expirationMillis;
        this.clock = clock;
    }

    @PostConstruct
    void initialize() {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public IssuedToken issue(Account account) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusMillis(expirationMillis);
        String value = Jwts.builder()
                .subject(account.publicId().toString())
                .claim("v", 3)
                .claim("aid", account.id())
                .claim("tv", account.tokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(value, expiresAt);
    }

    @Override
    public Optional<DecodedToken> decode(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Number version = claims.get("v", Number.class);
            Number accountId = claims.get("aid", Number.class);
            Number tokenVersion = claims.get("tv", Number.class);
            if (version == null || version.intValue() != 3 || accountId == null || tokenVersion == null) return Optional.empty();
            return Optional.of(new DecodedToken(accountId.longValue(), tokenVersion.intValue(), claims.getExpiration().toInstant()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
