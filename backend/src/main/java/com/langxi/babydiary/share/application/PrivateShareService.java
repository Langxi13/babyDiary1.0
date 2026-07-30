package com.langxi.babydiary.share.application;

import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.StepUpService;
import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaRepository;
import com.langxi.babydiary.media.application.MediaRepresentationService;
import com.langxi.babydiary.media.application.MediaView;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivateShareService {
    private final SpaceAccess spaces;
    private final PrivateShareRepository mapper;
    private final StepUpService stepUp;
    private final PasswordEncoder passwords;
    private final MediaRepository media;
    private final MediaRepresentationService representations;
    private final SecureRandom random = new SecureRandom();

    public PrivateShareService(
            SpaceAccess spaces,
            PrivateShareRepository mapper,
            StepUpService stepUp,
            PasswordEncoder passwords,
            MediaRepository media,
            MediaRepresentationService representations) {
        this.spaces = spaces;
        this.mapper = mapper;
        this.stepUp = stepUp;
        this.passwords = passwords;
        this.media = media;
        this.representations = representations;
    }

    @Transactional
    public Created create(
            UUID spaceId,
            UUID diaryId,
            AccountPrincipal principal,
            String stepUpToken,
            int hours,
            String password,
            Integer maxViews) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, principal.accountId());
        PrivateShareRepository.DiaryData diary = manageable(space, diaryId, principal.accountId());
        if (diary.locked())
            throw ApiException.conflict("LOCKED_CONTENT_NOT_SHAREABLE", "锁定日记不能创建公开分享");
        if (hours < 1 || hours > 720)
            throw ApiException.badRequest("SHARE_EXPIRY_INVALID", "分享有效期应为1小时到30天");
        if (maxViews != null && (maxViews < 1 || maxViews > 10000))
            throw ApiException.badRequest("SHARE_VIEWS_INVALID", "浏览次数应为1到10000");
        String normalized = password == null || password.isBlank() ? null : password;
        if (normalized != null && (normalized.length() < 4 || normalized.length() > 64))
            throw ApiException.badRequest("SHARE_PASSWORD_INVALID", "分享密码长度应为4到64位");
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        UUID id = UUID.randomUUID();
        LocalDateTime expires = LocalDateTime.now(ZoneOffset.UTC).plusHours(hours);
        mapper.insert(
                new PrivateShareRepository.NewShare(
                        BinaryUuid.toBytes(id),
                        sha256(token),
                        space.internalId(),
                        diary.diaryId(),
                        principal.accountId(),
                        normalized == null ? null : passwords.encode(normalized),
                        expires,
                        maxViews));
        return new Created(id, "/shared/" + token, expires, maxViews);
    }

    public List<Summary> list(
            UUID spaceId, UUID diaryId, AccountPrincipal principal, String stepUpToken) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, principal.accountId());
        PrivateShareRepository.DiaryData diary = manageable(space, diaryId, principal.accountId());
        if (diary.locked()) stepUp.require(principal, stepUpToken);
        return mapper.findActive(diary.diaryId(), principal.accountId()).stream()
                .map(this::summary)
                .toList();
    }

    @Transactional
    public void revoke(UUID shareId, long accountId) {
        if (mapper.revoke(BinaryUuid.toBytes(shareId), accountId) != 1)
            throw ApiException.notFound("SHARE_NOT_FOUND", "分享不存在或无权撤销");
    }

    @Transactional
    public SharedDiary open(String token, String password) {
        if (token == null || token.isBlank())
            throw ApiException.notFound("SHARE_NOT_FOUND", "分享不存在或已过期");
        PrivateShareRepository.OpenShare row = mapper.findForOpen(sha256(token));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (row == null
                || row.locked()
                || row.expiresAt().isBefore(now)
                || (row.maxViews() != null && row.viewCount() >= row.maxViews()))
            throw ApiException.notFound("SHARE_NOT_FOUND", "分享不存在或已过期");
        if (row.passwordHash() != null
                && (password == null || !passwords.matches(password, row.passwordHash())))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SHARE_PASSWORD_INVALID", "分享密码不正确");
        if (mapper.incrementView(row.shareId(), now) != 1)
            throw ApiException.notFound("SHARE_NOT_FOUND", "分享不存在或已过期");
        MediaAccessContext mediaContext =
                MediaAccessContext.share(BinaryUuid.fromBytes(row.publicId()));
        List<PrivateShareRepository.MediaLink> links = mapper.findMedia(row.diaryId());
        java.util.Map<UUID, MediaView> views =
                representations
                        .views(
                                media.findByPublicIdsInSpace(
                                        row.spaceId(),
                                        links.stream()
                                                .map(item -> BinaryUuid.fromBytes(item.publicId()))
                                                .toList()),
                                mediaContext)
                        .stream()
                        .collect(java.util.stream.Collectors.toMap(MediaView::id, value -> value));
        List<SharedMedia> sharedMedia =
                links.stream()
                        .map(
                                item -> {
                                    UUID id = BinaryUuid.fromBytes(item.publicId());
                                    MediaView view = views.get(id);
                                    MediaView.Representations reps =
                                            view == null ? null : view.representations();
                                    return new SharedMedia(
                                            id,
                                            item.mediaType(),
                                            item.caption(),
                                            item.takenAt(),
                                            item.position(),
                                            url(reps == null ? null : reps.original()),
                                            url(reps == null ? null : reps.thumbnail()),
                                            url(reps == null ? null : reps.poster()),
                                            url(reps == null ? null : reps.transcoded()));
                                })
                        .toList();
        return new SharedDiary(
                row.title(), row.diaryDate(), row.contentHtml(), row.moodKey(), sharedMedia);
    }

    private PrivateShareRepository.DiaryData manageable(
            SpaceAccess.SpaceContext space, UUID diaryId, long accountId) {
        PrivateShareRepository.DiaryData row =
                mapper.findManageableDiary(
                        space.internalId(),
                        BinaryUuid.toBytes(diaryId),
                        accountId,
                        "OWNER".equals(space.role()));
        if (row == null) throw ApiException.notFound("DIARY_NOT_FOUND", "日记不存在或无权管理分享");
        return row;
    }

    private Summary summary(PrivateShareRepository.ShareData row) {
        return new Summary(
                BinaryUuid.fromBytes(row.publicId()),
                row.expiresAt(),
                row.maxViews(),
                row.viewCount(),
                row.passwordHash() != null,
                row.createdAt());
    }

    private String url(MediaView.Representation representation) {
        return representation == null ? null : representation.url();
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record Created(UUID id, String sharePath, LocalDateTime expiresAt, Integer maxViews) {}

    public record Summary(
            UUID id,
            LocalDateTime expiresAt,
            Integer maxViews,
            int viewCount,
            boolean passwordProtected,
            LocalDateTime createdAt) {}

    public record SharedDiary(
            String title,
            LocalDate diaryDate,
            String contentHtml,
            String mood,
            List<SharedMedia> media) {}

    public record SharedMedia(
            UUID id,
            String mediaType,
            String caption,
            LocalDateTime takenAt,
            int position,
            String contentUrl,
            String thumbnailUrl,
            String posterUrl,
            String transcodedUrl) {}
}
