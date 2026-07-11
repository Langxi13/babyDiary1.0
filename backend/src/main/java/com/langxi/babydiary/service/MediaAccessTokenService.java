package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Service
public class MediaAccessTokenService {
    private final byte[] secret;

    public MediaAccessTokenService(@Value("${jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public SignedMediaUrl sign(String publicId, String variant) {
        long expires = Instant.now().plusSeconds(15 * 60).getEpochSecond();
        String signature = signature(publicId, variant, expires);
        String url = "/api/v2/media/public/" + publicId + "/" + variant
                + "?expires=" + expires + "&signature=" + signature;
        return new SignedMediaUrl(url, expires);
    }

    public void verify(String publicId, String variant, long expires, String suppliedSignature) {
        if (expires < Instant.now().getEpochSecond() || expires > Instant.now().plusSeconds(24 * 60 * 60).getEpochSecond()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "媒体链接已过期");
        }
        byte[] expected = signature(publicId, variant, expires).getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = suppliedSignature == null ? new byte[0] : suppliedSignature.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "媒体链接签名无效");
        }
    }

    private String signature(String publicId, String variant, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] value = mac.doFinal((publicId + ":" + variant + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }

    public record SignedMediaUrl(String url, long expires) {}
}
