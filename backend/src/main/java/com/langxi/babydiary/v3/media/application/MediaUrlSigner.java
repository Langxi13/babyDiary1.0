package com.langxi.babydiary.v3.media.application;

import com.langxi.babydiary.v3.platform.application.V3Exception;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class MediaUrlSigner {
    private static final Pattern PROFILE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,31}");
    private final String secret;
    private final Duration lifetime;
    private final Clock clock;
    private byte[] key;

    @Autowired
    public MediaUrlSigner(@Value("${app.v3.media.url-signing-key:${jwt.secret}}") String secret,
                          @Value("${app.v3.media.url-lifetime-seconds:3600}") long lifetimeSeconds) {
        this(secret, Duration.ofSeconds(Math.max(60, lifetimeSeconds)), Clock.systemUTC());
    }

    MediaUrlSigner(String secret, Duration lifetime, Clock clock) {
        this.secret = secret;
        this.lifetime = lifetime;
        this.clock = clock;
    }

    @PostConstruct
    void initialize() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("Media URL signing key must contain at least 32 characters");
        }
        key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String url(UUID spaceId, UUID assetId, String variant, String profile) {
        String normalized = normalize(variant);
        String normalizedProfile = normalizeProfile(profile);
        long expires = clock.instant().plus(lifetime).getEpochSecond();
        String signature = sign(payload(spaceId, assetId, normalized, normalizedProfile, expires));
        return "/api/v3/public/media/" + spaceId + "/" + assetId + "/"
                + normalized.toLowerCase(Locale.ROOT) + "?profile=" + normalizedProfile
                + "&expires=" + expires + "&signature=" + signature;
    }

    public VerifiedVariant verify(UUID spaceId, UUID assetId, String variant, String profile,
                                  long expires, String signature) {
        String normalized = normalize(variant);
        if (expires < clock.instant().getEpochSecond()) {
            throw V3Exception.notFound("MEDIA_URL_EXPIRED", "媒体访问地址已过期");
        }
        boolean legacy = profile == null || profile.isBlank();
        String normalizedProfile = legacy ? null : normalizeProfile(profile);
        String expected = sign(legacy
                ? legacyPayload(spaceId, assetId, normalized, expires)
                : payload(spaceId, assetId, normalized, normalizedProfile, expires));
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        byte[] actualBytes = String.valueOf(signature).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw V3Exception.notFound("MEDIA_URL_INVALID", "媒体访问地址无效");
        }
        return new VerifiedVariant(normalized, normalizedProfile, legacy);
    }

    private String normalize(String variant) {
        return variant == null || variant.isBlank() ? "ORIGINAL" : variant.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeProfile(String profile) {
        String normalized = profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT);
        if (!PROFILE.matcher(normalized).matches()) {
            throw V3Exception.notFound("MEDIA_URL_INVALID", "媒体访问地址无效");
        }
        return normalized;
    }

    private String payload(UUID spaceId, UUID assetId, String variant, String profile, long expires) {
        return spaceId + "\n" + assetId + "\n" + variant + "\n" + profile + "\n" + expires;
    }

    private String legacyPayload(UUID spaceId, UUID assetId, String variant, long expires) {
        return spaceId + "\n" + assetId + "\n" + variant + "\n" + expires;
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

    public record VerifiedVariant(String type, String profile, boolean legacy) {
    }
}
