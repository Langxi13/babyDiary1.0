package com.langxi.babydiary.share.infrastructure;

import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.share.application.PrivateShareRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisPrivateShareRepository implements PrivateShareRepository {
    private final PrivateShareMapper mapper;

    public MyBatisPrivateShareRepository(PrivateShareMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DiaryData findManageableDiary(
            long spaceId, byte[] diaryPublicId, long accountId, boolean spaceOwner) {
        PrivateShareMapper.DiaryRow row =
                mapper.findManageableDiary(spaceId, diaryPublicId, accountId, spaceOwner);
        return row == null ? null : new DiaryData(row.getDiaryId(), row.isLocked());
    }

    @Override
    public void insert(NewShare share) {
        mapper.insert(
                new PrivateShareMapper.ShareInsert(
                        share.publicId(),
                        share.tokenHash(),
                        share.spaceId(),
                        share.diaryId(),
                        share.createdBy(),
                        share.passwordHash(),
                        share.expiresAt(),
                        share.maxViews()));
    }

    @Override
    public List<ShareData> findActive(long diaryId, long accountId) {
        return mapper.findActive(diaryId, accountId).stream()
                .map(
                        row ->
                                new ShareData(
                                        row.getShareId(),
                                        row.getPublicId(),
                                        row.getPasswordHash(),
                                        row.getExpiresAt(),
                                        row.getMaxViews(),
                                        row.getViewCount(),
                                        row.getCreatedAt()))
                .toList();
    }

    @Override
    public OpenShare findForOpen(byte[] tokenHash) {
        PrivateShareMapper.OpenRow row = mapper.findForOpen(tokenHash);
        return row == null
                ? null
                : new OpenShare(
                        row.getShareId(),
                        row.getPublicId(),
                        row.getPasswordHash(),
                        row.getExpiresAt(),
                        row.getMaxViews(),
                        row.getViewCount(),
                        row.getSpaceId(),
                        BinaryUuid.fromBytes(row.getSpacePublicId()),
                        row.getDiaryId(),
                        row.isLocked(),
                        row.getTitle(),
                        row.getDiaryDate(),
                        row.getContentHtml(),
                        row.getMoodKey());
    }

    @Override
    public int incrementView(long shareId, LocalDateTime now) {
        return mapper.incrementView(shareId, now);
    }

    @Override
    public int revoke(byte[] publicId, long accountId) {
        return mapper.revoke(publicId, accountId);
    }

    @Override
    public List<MediaLink> findMedia(long diaryId) {
        return mapper.findMedia(diaryId).stream()
                .map(
                        row ->
                                new MediaLink(
                                        row.publicId(),
                                        row.mediaType(),
                                        row.caption(),
                                        row.takenAt(),
                                        row.position()))
                .toList();
    }
}
