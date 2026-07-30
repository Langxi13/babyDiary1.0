package com.langxi.babydiary.v3.media.application;

import com.langxi.babydiary.v3.platform.application.V3Exception;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Component
public class MediaUrlSigner {
    private static final long BUCKET_SECONDS = 300;
    private final String secret;
    private final Duration lifetime;
    private final Duration protectedLifetime;
    private final Clock clock;
    private byte[] key;

    @Autowired
    public MediaUrlSigner(@Value("${app.v3.media.url-signing-key:${jwt.secret}}") String secret,
                          @Value("${app.v3.media.url-lifetime-seconds:1800}") long lifetimeSeconds,
                          @Value("${app.v3.media.protected-url-lifetime-seconds:300}") long protectedSeconds) {
        this(secret, Duration.ofSeconds(Math.max(60, lifetimeSeconds)),
                Duration.ofSeconds(Math.max(60, protectedSeconds)), Clock.systemUTC());
    }

    MediaUrlSigner(String secret, Duration lifetime, Duration protectedLifetime, Clock clock) {
        this.secret = secret;
        this.lifetime = lifetime;
        this.protectedLifetime = protectedLifetime;
        this.clock = clock;
    }

    @PostConstruct
    void initialize() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("Media URL signing key must contain at least 32 characters");
        }
        key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public SignedUrl url(UUID spaceId, UUID assetId, String variant, String profile,
                         MediaAccessContext context) {
        String normalized = normalizeVariant(variant);
        String normalizedProfile = normalizeProfile(profile);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(
                context.serialize().getBytes(StandardCharsets.UTF_8));
        Duration selected = context.elevated() || context.source() == MediaAccessContext.Source.SHARE
                ? protectedLifetime : lifetime;
        long now = clock.instant().getEpochSecond();
        long expires = ((now + selected.toSeconds() + BUCKET_SECONDS - 1) / BUCKET_SECONDS) * BUCKET_SECONDS;
        if (expires <= now) expires = now + 60;
        String signature = sign(payload(spaceId, assetId, normalized, normalizedProfile, ticket, expires));
        String value = "/api/v3/public/media/" + spaceId + "/" + assetId + "/"
                + normalized.toLowerCase(Locale.ROOT) + "?profile=" + normalizedProfile
                + "&ticket=" + ticket + "&expires=" + expires + "&signature=" + signature;
        return new SignedUrl(value, Instant.ofEpochSecond(expires));
    }

    public VerifiedVariant verify(UUID spaceId, UUID assetId, String variant, String profile,
                                  String ticket, long expires, String signature) {
        String normalized = normalizeVariant(variant);
        String normalizedProfile = normalizeProfile(profile);
        long now = clock.instant().getEpochSecond();
        if (expires < now) throw V3Exception.notFound("MEDIA_URL_EXPIRED", "媒体访问地址已过期");
        if (expires > now + Math.max(lifetime.toSeconds(), protectedLifetime.toSeconds()) + BUCKET_SECONDS) {
            throw V3Exception.notFound("MEDIA_URL_INVALID", "媒体访问地址无效");
        }
        String expected = sign(payload(spaceId, assetId, normalized, normalizedProfile, ticket, expires));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                String.valueOf(signature).getBytes(StandardCharsets.US_ASCII))) {
            throw V3Exception.notFound("MEDIA_URL_INVALID", "媒体访问地址无效");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(ticket), StandardCharsets.UTF_8);
            return new VerifiedVariant(normalized, normalizedProfile, MediaAccessContext.parse(decoded),
                    Instant.ofEpochSecond(expires));
        } catch (IllegalArgumentException exception) {
            throw V3Exception.notFound("MEDIA_URL_INVALID", "媒体访问地址无效");
        }
    }

    private String normalizeVariant(String variant) {
        String value = variant == null || variant.isBlank() ? "ORIGINAL" : variant.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw V3Exception.notFound("MEDIA_URL_INVALID", "媒体访问地址无效");
        }
        return value;
    }

    private String normalizeProfile(String profile) {
        String value = profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,31}")) {
            throw V3Exception.notFound("MEDIA_URL_INVALID", "媒体访问地址无效");
        }
        return value;
    }

    private String payload(UUID spaceId, UUID assetId, String variant, String profile,
                           String ticket, long expires) {
        return spaceId + "\n" + assetId + "\n" + variant + "\n" + profile + "\n" + ticket + "\n" + expires;
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign media URL", exception);
        }
    }

    public record SignedUrl(String url, Instant expiresAt) {
    }

    public record VerifiedVariant(String type, String profile, MediaAccessContext context, Instant expiresAt) {
    }
}
