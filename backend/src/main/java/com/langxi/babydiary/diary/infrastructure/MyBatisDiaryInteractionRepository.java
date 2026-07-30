package com.langxi.babydiary.diary.infrastructure;

import com.langxi.babydiary.diary.application.DiaryInteractionRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDiaryInteractionRepository implements DiaryInteractionRepository {
    private final DiaryInteractionMapper mapper;

    public MyBatisDiaryInteractionRepository(DiaryInteractionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DiaryAccess findVisibleDiary(long spaceId, byte[] diaryPublicId, long accountId) {
        DiaryInteractionMapper.DiaryAccessRow row =
                mapper.findVisibleDiary(spaceId, diaryPublicId, accountId);
        return row == null ? null : new DiaryAccess(row.getDiaryId(), row.isLocked());
    }

    @Override
    public List<CommentData> findComments(long diaryId) {
        return mapper.findComments(diaryId).stream()
                .map(
                        row ->
                                new CommentData(
                                        row.getPublicId(),
                                        row.getAuthorPublicId(),
                                        row.getUsername(),
                                        row.getContent(),
                                        row.getCreatedAt(),
                                        row.getUpdatedAt(),
                                        row.getAvatarAssetPublicId(),
                                        row.getAvatarSpacePublicId(),
                                        row.getAvatarVariantType(),
                                        row.getAvatarVariantProfile()))
                .toList();
    }

    @Override
    public void insertComment(byte[] publicId, long diaryId, long authorId, String content) {
        mapper.insertComment(
                new DiaryInteractionMapper.CommentInsert(publicId, diaryId, authorId, content));
    }

    @Override
    public int updateComment(long diaryId, byte[] publicId, long authorId, String content) {
        return mapper.updateComment(diaryId, publicId, authorId, content);
    }

    @Override
    public int deleteComment(long diaryId, byte[] publicId, long authorId) {
        return mapper.deleteComment(diaryId, publicId, authorId);
    }

    @Override
    public List<ReactionData> findReactions(long diaryId, long accountId) {
        return mapper.findReactions(diaryId, accountId).stream()
                .map(row -> new ReactionData(row.emoji(), row.reactionCount(), row.reactedByMe()))
                .toList();
    }

    @Override
    public void insertReaction(long diaryId, long accountId, String emoji) {
        mapper.insertReaction(diaryId, accountId, emoji);
    }

    @Override
    public void deleteReaction(long diaryId, long accountId, String emoji) {
        mapper.deleteReaction(diaryId, accountId, emoji);
    }
}
