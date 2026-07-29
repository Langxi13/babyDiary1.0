package com.langxi.babydiary.v3.identity.application;

import com.langxi.babydiary.v3.media.application.MediaRepository;
import com.langxi.babydiary.v3.media.domain.MediaAsset;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.space.application.SpaceAccess;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProfileService {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{2,100}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final ProfileRepository profiles;
    private final SpaceAccess spaces;
    private final MediaRepository media;
    private final PasswordEncoder passwords;

    public ProfileService(ProfileRepository profiles, SpaceAccess spaces, MediaRepository media,
                          PasswordEncoder passwords) {
        this.profiles = profiles;
        this.spaces = spaces;
        this.media = media;
        this.passwords = passwords;
    }

    public ProfileRepository.Profile profile(long accountId) {
        return profiles.find(accountId).orElseThrow(() -> V3Exception.notFound("ACCOUNT_NOT_FOUND", "账户不存在"));
    }

    @Transactional
    public ProfileRepository.Profile update(long accountId, String username, String email, String timezone) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (!USERNAME.matcher(normalizedUsername).matches()) {
            throw V3Exception.badRequest("USERNAME_INVALID", "用户名仅支持字母、数字、点、下划线和短横线，长度为2至100个字符");
        }
        String normalizedEmail = email == null || email.isBlank() ? null : email.trim();
        if (normalizedEmail != null && (normalizedEmail.length() > 255 || !EMAIL.matcher(normalizedEmail).matches())) {
            throw V3Exception.badRequest("EMAIL_INVALID", "邮箱格式无效");
        }
        String normalizedTimezone = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone.trim();
        try {
            java.time.ZoneId.of(normalizedTimezone);
        } catch (ZoneRulesException exception) {
            throw V3Exception.badRequest("TIMEZONE_INVALID", "时区无效");
        }
        try {
            profiles.update(accountId, normalizedUsername, normalizedEmail, normalizedTimezone);
        } catch (DuplicateKeyException exception) {
            throw new V3Exception(org.springframework.http.HttpStatus.CONFLICT, "ACCOUNT_FIELD_EXISTS", "用户名或邮箱已被使用");
        }
        return profile(accountId);
    }

    @Transactional
    public ProfileRepository.Profile setAvatar(long accountId, UUID spaceId, UUID assetId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        MediaAsset asset = media.findByPublicIds(space.internalId(), List.of(assetId), accountId).stream().findFirst()
                .filter(value -> value.ownerId() == accountId && "IMAGE".equals(value.mediaType()))
                .orElseThrow(() -> V3Exception.badRequest("AVATAR_MEDIA_INVALID", "头像图片不存在、不是图片或不属于当前账户"));
        profiles.setAvatar(accountId, space.internalId(), asset.internalId());
        return profile(accountId);
    }

    @Transactional
    public void clearAvatar(long accountId) {
        profiles.clearAvatar(accountId);
    }

    @Transactional
    public void changePassword(long accountId, String currentPassword, String nextPassword) {
        ProfileRepository.Profile profile = profile(accountId);
        if (!passwords.matches(currentPassword, profile.passwordHash())) {
            throw new V3Exception(org.springframework.http.HttpStatus.UNAUTHORIZED, "PASSWORD_INVALID", "当前密码错误");
        }
        if (nextPassword == null || nextPassword.length() < 8 || nextPassword.length() > 200) {
            throw V3Exception.badRequest("PASSWORD_WEAK", "新密码长度需为8至200个字符");
        }
        profiles.changePassword(accountId, passwords.encode(nextPassword), LocalDateTime.now(ZoneOffset.UTC));
    }
}
