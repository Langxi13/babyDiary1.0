package com.langxi.babydiary.identity.application;

import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.SecretCodec;
import com.langxi.babydiary.platform.application.SecretCodecFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationCodeService implements ApplicationRunner {
    private final InvitationCodeRepository codes;
    private final ProfileRepository profiles;
    private final StepUpService stepUp;
    private final SecretCodec secret;
    private final SecureRandom random = new SecureRandom();
    private final String bootstrapCode;

    public InvitationCodeService(
            InvitationCodeRepository codes,
            ProfileRepository profiles,
            StepUpService stepUp,
            SecretCodecFactory secretCodecs,
            @Value("${invitation-code.encryption-key:${INVITATION_CODE_ENCRYPTION_KEY:}}")
                    String encryptionKey,
            @Value("${app.invitation.bootstrap-code:${INVITATION_CODE:}}") String bootstrapCode) {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalArgumentException("Invitation code encryption key must be configured");
        }
        this.codes = codes;
        this.profiles = profiles;
        this.stepUp = stepUp;
        this.secret = secretCodecs.create(encryptionKey);
        this.bootstrapCode = bootstrapCode == null ? "" : bootstrapCode.trim();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String encrypted = codes.findEncryptedForUpdate();
        if (encrypted != null) {
            decrypt(encrypted);
            return;
        }
        if (bootstrapCode.isBlank()) {
            throw new IllegalStateException(
                    "System invitation code is not initialized; provide INVITATION_CODE for first startup");
        }
        codes.upsert(secret.encrypt(bootstrapCode), null);
    }

    @Transactional(readOnly = true)
    public String view(AccountPrincipal principal, String token) {
        verify(principal, token);
        String encrypted = codes.findEncrypted();
        if (encrypted == null || encrypted.isBlank())
            throw ApiException.notFound("INVITATION_CODE_MISSING", "邀请码尚未配置");
        try {
            return secret.decrypt(encrypted);
        } catch (RuntimeException exception) {
            throw ApiException.badRequest("INVITATION_CODE_INVALID", "邀请码配置无法解密");
        }
    }

    @Transactional
    public String rotate(AccountPrincipal principal, String token) {
        verify(principal, token);
        String code = "BD-" + randomPart(12);
        codes.upsert(secret.encrypt(code), principal.accountId());
        return code;
    }

    @Transactional(readOnly = true)
    public boolean matches(String candidate) {
        String encrypted = codes.findEncrypted();
        if (encrypted == null || candidate == null) return false;
        try {
            byte[] expected = decrypt(encrypted).getBytes(StandardCharsets.UTF_8);
            byte[] actual = candidate.trim().getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Transactional
    public boolean matchesForRegistration(String candidate) {
        String encrypted = codes.findEncryptedForUpdate();
        if (encrypted == null || candidate == null) return false;
        byte[] expected = decrypt(encrypted).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, candidate.trim().getBytes(StandardCharsets.UTF_8));
    }

    private String decrypt(String encrypted) {
        try {
            return secret.decrypt(encrypted);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "Stored invitation code cannot be decrypted with the configured key",
                    exception);
        }
    }

    private void verify(AccountPrincipal principal, String token) {
        if (principal == null || !"ADMIN".equals(principal.role()))
            throw ApiException.forbidden("ADMIN_REQUIRED", "只有管理员可以管理邀请码");
        stepUp.require(principal, token);
    }

    private String randomPart(int length) {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder value = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            value.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return value.toString();
    }
}
