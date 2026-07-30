package com.langxi.babydiary.diary.application;

import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaUrlSigner;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryInteractionService {
    private final SpaceAccess spaces;
    private final DiaryInteractionRepository mapper;
    private final MediaUrlSigner mediaUrls;

    public DiaryInteractionService(
            SpaceAccess spaces, DiaryInteractionRepository mapper, MediaUrlSigner mediaUrls) {
        this.spaces = spaces;
        this.mapper = mapper;
        this.mediaUrls = mediaUrls;
    }

    public List<Comment> comments(UUID spaceId, UUID diaryId, long accountId, boolean elevated) {
        long internalId = requireDiary(spaceId, diaryId, accountId, elevated);
        return mapper.findComments(internalId).stream()
                .map(row -> comment(row, accountId))
                .toList();
    }

    @Transactional
    public Comment addComment(
            UUID spaceId, UUID diaryId, long accountId, String content, boolean elevated) {
        long internalId = requireDiary(spaceId, diaryId, accountId, elevated);
        String value = content(content);
        UUID publicId = UUID.randomUUID();
        mapper.insertComment(BinaryUuid.toBytes(publicId), internalId, accountId, value);
        return mapper.findComments(internalId).stream()
                .filter(
                        row ->
                                java.util.Arrays.equals(
                                        row.publicId(), BinaryUuid.toBytes(publicId)))
                .findFirst()
                .map(row -> comment(row, accountId))
                .orElseThrow();
    }

    @Transactional
    public void updateComment(
            UUID spaceId,
            UUID diaryId,
            UUID commentId,
            long accountId,
            String content,
            boolean elevated) {
        long internalId = requireDiary(spaceId, diaryId, accountId, elevated);
        if (mapper.updateComment(
                        internalId, BinaryUuid.toBytes(commentId), accountId, content(content))
                != 1) {
            throw ApiException.notFound("COMMENT_NOT_FOUND", "评论不存在或无权修改");
        }
    }

    @Transactional
    public void deleteComment(
            UUID spaceId, UUID diaryId, UUID commentId, long accountId, boolean elevated) {
        long internalId = requireDiary(spaceId, diaryId, accountId, elevated);
        if (mapper.deleteComment(internalId, BinaryUuid.toBytes(commentId), accountId) != 1) {
            throw ApiException.notFound("COMMENT_NOT_FOUND", "评论不存在或无权删除");
        }
    }

    public List<Reaction> reactions(UUID spaceId, UUID diaryId, long accountId, boolean elevated) {
        long internalId = requireDiary(spaceId, diaryId, accountId, elevated);
        return mapper.findReactions(internalId, accountId).stream()
                .map(row -> new Reaction(row.emoji(), row.reactionCount(), row.reactedByMe()))
                .toList();
    }

    @Transactional
    public void setReaction(
            UUID spaceId,
            UUID diaryId,
            long accountId,
            String emoji,
            boolean active,
            boolean elevated) {
        long internalId = requireDiary(spaceId, diaryId, accountId, elevated);
        String value = emoji == null ? "" : emoji.trim();
        if (value.isBlank() || value.length() > 16) {
            throw ApiException.badRequest("REACTION_INVALID", "表情无效");
        }
        if (active) mapper.insertReaction(internalId, accountId, value);
        else mapper.deleteReaction(internalId, accountId, value);
    }

    private long requireDiary(UUID spaceId, UUID diaryId, long accountId, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        DiaryInteractionRepository.DiaryAccess row =
                mapper.findVisibleDiary(space.internalId(), BinaryUuid.toBytes(diaryId), accountId);
        if (row == null) throw ApiException.notFound("DIARY_NOT_FOUND", "日记不存在或无权访问");
        if (row.locked() && !elevated)
            throw new ApiException(HttpStatus.LOCKED, "STEP_UP_REQUIRED", "请先完成二次验证");
        return row.diaryId();
    }

    private Comment comment(DiaryInteractionRepository.CommentData row, long viewerAccountId) {
        AvatarMedia avatar = null;
        if (row.avatarAssetPublicId() != null
                && row.avatarSpacePublicId() != null
                && row.avatarVariantType() != null
                && row.avatarVariantProfile() != null) {
            UUID assetId = BinaryUuid.fromBytes(row.avatarAssetPublicId());
            UUID spaceId = BinaryUuid.fromBytes(row.avatarSpacePublicId());
            UUID authorPublicId = BinaryUuid.fromBytes(row.authorPublicId());
            avatar =
                    new AvatarMedia(
                            assetId,
                            mediaUrls
                                    .url(
                                            spaceId,
                                            assetId,
                                            row.avatarVariantType(),
                                            row.avatarVariantProfile(),
                                            MediaAccessContext.avatar(
                                                    viewerAccountId, authorPublicId, false))
                                    .url());
        }
        UUID authorId = BinaryUuid.fromBytes(row.authorPublicId());
        return new Comment(
                BinaryUuid.fromBytes(row.publicId()),
                authorId,
                authorId,
                row.username(),
                row.content(),
                avatar,
                row.createdAt(),
                row.updatedAt());
    }

    private String content(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isBlank() || result.length() > 2000) {
            throw ApiException.badRequest("COMMENT_CONTENT_INVALID", "评论内容不能为空且最多2000字");
        }
        return result;
    }

    public record Comment(
            UUID publicId,
            UUID id,
            UUID userId,
            String username,
            String content,
            AvatarMedia avatarMedia,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record AvatarMedia(UUID assetId, String contentUrl) {}

    public record Reaction(String emoji, long count, boolean reactedByMe) {}
}
