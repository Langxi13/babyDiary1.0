package com.langxi.babydiary.diary.application;

import java.time.LocalDateTime;
import java.util.List;

public interface DiaryInteractionRepository {
    DiaryAccess findVisibleDiary(long spaceId, byte[] diaryPublicId, long accountId);

    List<CommentData> findComments(long diaryId);

    void insertComment(byte[] publicId, long diaryId, long authorId, String content);

    int updateComment(long diaryId, byte[] publicId, long authorId, String content);

    int deleteComment(long diaryId, byte[] publicId, long authorId);

    List<ReactionData> findReactions(long diaryId, long accountId);

    void insertReaction(long diaryId, long accountId, String emoji);

    void deleteReaction(long diaryId, long accountId, String emoji);

    record DiaryAccess(long diaryId, boolean locked) {}

    record CommentData(
            byte[] publicId,
            byte[] authorPublicId,
            String username,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            byte[] avatarAssetPublicId,
            byte[] avatarSpacePublicId,
            String avatarVariantType,
            String avatarVariantProfile) {}

    record ReactionData(String emoji, long reactionCount, boolean reactedByMe) {}
}
