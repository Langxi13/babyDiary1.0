package com.langxi.babydiary.identity.application;

import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaAccessPolicy;
import com.langxi.babydiary.media.application.MediaRepository;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.AfterCommit;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{2,100}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final ProfileRepository profiles;
    private final SpaceAccess spaces;
    private final MediaRepository media;
    private final PasswordEncoder passwords;
    private final MediaAccessPolicy mediaAccess;
    private final CredentialRepository credentials;
    private final AuthenticationProjectionCache authenticationCache;

    public ProfileService(
            ProfileRepository profiles,
            SpaceAccess spaces,
            MediaRepository media,
            PasswordEncoder passwords,
            MediaAccessPolicy mediaAccess,
            CredentialRepository credentials,
            AuthenticationProjectionCache authenticationCache) {
        this.profiles = profiles;
        this.spaces = spaces;
        this.media = media;
        this.passwords = passwords;
        this.mediaAccess = mediaAccess;
        this.credentials = credentials;
        this.authenticationCache = authenticationCache;
    }

    public ProfileView profile(long accountId) {
        return toView(requireProfile(accountId));
    }

    @Transactional
    public ProfileView update(long accountId, String username, String email, String timezone) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (!USERNAME.matcher(normalizedUsername).matches()) {
            throw ApiException.badRequest("USERNAME_INVALID", "用户名仅支持字母、数字、点、下划线和短横线，长度为2至100个字符");
        }
        String normalizedEmail = email == null || email.isBlank() ? null : email.trim();
        if (normalizedEmail != null
                && (normalizedEmail.length() > 255 || !EMAIL.matcher(normalizedEmail).matches())) {
            throw ApiException.badRequest("EMAIL_INVALID", "邮箱格式无效");
        }
        String normalizedTimezone =
                timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim();
        try {
            java.time.ZoneId.of(normalizedTimezone);
        } catch (ZoneRulesException exception) {
            throw ApiException.badRequest("TIMEZONE_INVALID", "时区无效");
        }
        try {
            profiles.update(accountId, normalizedUsername, normalizedEmail, normalizedTimezone);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "ACCOUNT_FIELD_EXISTS",
                    "用户名或邮箱已被使用");
        }
        AfterCommit.run(() -> authenticationCache.invalidate(accountId));
        return profile(accountId);
    }

    @Transactional
    public ProfileView setAvatar(long accountId, UUID spaceId, UUID assetId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        if (mediaAccess.isProtected(spaceId, assetId)) {
            throw ApiException.badRequest("AVATAR_MEDIA_PROTECTED", "锁定日记中的图片不能设为头像");
        }
        mediaAccess.require(spaceId, assetId, MediaAccessContext.direct(accountId, false));
        MediaAsset asset =
                media.findByPublicIds(space.internalId(), List.of(assetId), accountId).stream()
                        .findFirst()
                        .filter(
                                value ->
                                        value.ownerId() == accountId
                                                && "IMAGE".equals(value.mediaType()))
                        .orElseThrow(
                                () ->
                                        ApiException.badRequest(
                                                "AVATAR_MEDIA_INVALID", "头像图片不存在、不是图片或不属于当前账户"));
        profiles.setAvatar(accountId, space.internalId(), asset.internalId());
        return profile(accountId);
    }

    @Transactional
    public void clearAvatar(long accountId) {
        profiles.clearAvatar(accountId);
    }

    @Transactional
    public void changePassword(long accountId, String currentPassword, String nextPassword) {
        String passwordHash =
                credentials
                        .findPasswordHash(accountId)
                        .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "账户不存在"));
        if (!passwords.matches(currentPassword, passwordHash)) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "PASSWORD_INVALID", "当前密码错误");
        }
        if (nextPassword == null || nextPassword.length() < 8 || nextPassword.length() > 200) {
            throw ApiException.badRequest("PASSWORD_WEAK", "新密码长度需为8至200个字符");
        }
        credentials.changePassword(
                accountId, passwords.encode(nextPassword), LocalDateTime.now(ZoneOffset.UTC));
        AfterCommit.run(() -> authenticationCache.invalidate(accountId));
    }

    private ProfileRepository.Profile requireProfile(long accountId) {
        return profiles.find(accountId)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "账户不存在"));
    }

    private ProfileView toView(ProfileRepository.Profile profile) {
        return new ProfileView(
                profile.id(),
                profile.username(),
                profile.email(),
                profile.emailVerified(),
                profile.role(),
                profile.timezone(),
                profile.createdAt(),
                profile.avatarAssetId(),
                profile.avatarSpaceId(),
                profile.avatarVariantType(),
                profile.avatarVariantProfile());
    }

    public record ProfileView(
            UUID id,
            String username,
            String email,
            boolean emailVerified,
            String role,
            String timezone,
            LocalDateTime createdAt,
            UUID avatarAssetId,
            UUID avatarSpaceId,
            String avatarVariantType,
            String avatarVariantProfile) {}
}
