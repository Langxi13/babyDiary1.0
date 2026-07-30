package com.langxi.babydiary.v3.diary.application;

import com.langxi.babydiary.v3.diary.infrastructure.DiaryInteractionMapper;
import com.langxi.babydiary.v3.media.application.MediaUrlSigner;
import com.langxi.babydiary.v3.platform.application.BinaryUuid;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.space.application.SpaceAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DiaryInteractionService {
    private final SpaceAccess spaces;
    private final DiaryInteractionMapper mapper;
    private final MediaUrlSigner mediaUrls;

    public DiaryInteractionService(SpaceAccess spaces, DiaryInteractionMapper mapper, MediaUrlSigner mediaUrls) {
        this.spaces = spaces; this.mapper = mapper; this.mediaUrls = mediaUrls;
    }

    public List<Comment> comments(UUID spaceId, UUID diaryId, long accountId) {
        long internalId = requireDiary(spaceId, diaryId, accountId);
        return mapper.findComments(internalId).stream().map(this::comment).toList();
    }

    @Transactional
    public Comment addComment(UUID spaceId, UUID diaryId, long accountId, String content) {
        long internalId = requireDiary(spaceId, diaryId, accountId);
        String value = content(content);
        UUID publicId = UUID.randomUUID();
        mapper.insertComment(new DiaryInteractionMapper.CommentInsert(
                BinaryUuid.toBytes(publicId), internalId, accountId, value));
        return mapper.findComments(internalId).stream()
                .filter(row -> java.util.Arrays.equals(row.getPublicId(), BinaryUuid.toBytes(publicId)))
                .findFirst().map(this::comment).orElseThrow();
    }

    @Transactional
    public void updateComment(UUID spaceId, UUID diaryId, UUID commentId, long accountId, String content) {
        long internalId = requireDiary(spaceId, diaryId, accountId);
        if (mapper.updateComment(internalId, BinaryUuid.toBytes(commentId), accountId, content(content)) != 1) {
            throw V3Exception.notFound("COMMENT_NOT_FOUND", "评论不存在或无权修改");
        }
    }

    @Transactional
    public void deleteComment(UUID spaceId, UUID diaryId, UUID commentId, long accountId) {
        long internalId = requireDiary(spaceId, diaryId, accountId);
        if (mapper.deleteComment(internalId, BinaryUuid.toBytes(commentId), accountId) != 1) {
            throw V3Exception.notFound("COMMENT_NOT_FOUND", "评论不存在或无权删除");
        }
    }

    public List<Reaction> reactions(UUID spaceId, UUID diaryId, long accountId) {
        long internalId = requireDiary(spaceId, diaryId, accountId);
        return mapper.findReactions(internalId, accountId).stream()
                .map(row -> new Reaction(row.emoji(), row.reactionCount(), row.reactedByMe())).toList();
    }

    @Transactional
    public void setReaction(UUID spaceId, UUID diaryId, long accountId, String emoji, boolean active) {
        long internalId = requireDiary(spaceId, diaryId, accountId);
        String value = emoji == null ? "" : emoji.trim();
        if (value.isBlank() || value.length() > 16) {
            throw V3Exception.badRequest("REACTION_INVALID", "表情无效");
        }
        if (active) mapper.insertReaction(internalId, accountId, value);
        else mapper.deleteReaction(internalId, accountId, value);
    }

    private long requireDiary(UUID spaceId, UUID diaryId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        Long id = mapper.findVisibleDiary(space.internalId(), BinaryUuid.toBytes(diaryId), accountId);
        if (id == null) throw V3Exception.notFound("DIARY_NOT_FOUND", "日记不存在或无权访问");
        return id;
    }

    private Comment comment(DiaryInteractionMapper.CommentRow row) {
        AvatarMedia avatar = null;
        if (row.getAvatarAssetPublicId() != null && row.getAvatarSpacePublicId() != null
                && row.getAvatarVariantType() != null && row.getAvatarVariantProfile() != null) {
            UUID assetId = BinaryUuid.fromBytes(row.getAvatarAssetPublicId());
            UUID spaceId = BinaryUuid.fromBytes(row.getAvatarSpacePublicId());
            avatar = new AvatarMedia(assetId, mediaUrls.url(spaceId, assetId, row.getAvatarVariantType(),
                    row.getAvatarVariantProfile()));
        }
        UUID authorId = BinaryUuid.fromBytes(row.getAuthorPublicId());
        return new Comment(BinaryUuid.fromBytes(row.getPublicId()), authorId, authorId, row.getUsername(),
                row.getContent(), avatar, row.getCreatedAt(), row.getUpdatedAt());
    }

    private String content(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isBlank() || result.length() > 2000) {
            throw V3Exception.badRequest("COMMENT_CONTENT_INVALID", "评论内容不能为空且最多2000字");
        }
        return result;
    }

    public record Comment(UUID publicId, UUID id, UUID userId, String username, String content,
                          AvatarMedia avatarMedia, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record AvatarMedia(UUID assetId, String contentUrl) {}
    public record Reaction(String emoji, long count, boolean reactedByMe) {}
}
