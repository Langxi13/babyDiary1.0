package com.langxi.babydiary.media.infrastructure;

import com.langxi.babydiary.media.application.MediaAccessRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisMediaAccessRepository implements MediaAccessRepository {
    private final MediaAccessMapper mapper;

    public MyBatisMediaAccessRepository(MediaAccessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AccessDecision direct(byte[] spaceId, byte[] assetId, long accountId) {
        return decision(mapper.direct(spaceId, assetId, accountId));
    }

    @Override
    public AccessDecision diary(byte[] spaceId, byte[] assetId, byte[] diaryId, long accountId) {
        return decision(mapper.diary(spaceId, assetId, diaryId, accountId));
    }

    @Override
    public AccessDecision album(byte[] spaceId, byte[] assetId, byte[] albumId, long accountId) {
        return decision(mapper.album(spaceId, assetId, albumId, accountId));
    }

    @Override
    public AccessDecision anniversary(
            byte[] spaceId, byte[] assetId, byte[] anniversaryId, long accountId) {
        return decision(mapper.anniversary(spaceId, assetId, anniversaryId, accountId));
    }

    @Override
    public AccessDecision avatar(byte[] spaceId, byte[] assetId, byte[] accountId) {
        return decision(mapper.avatar(spaceId, assetId, accountId));
    }

    @Override
    public AccessDecision share(byte[] spaceId, byte[] assetId, byte[] shareId) {
        return decision(mapper.share(spaceId, assetId, shareId));
    }

    @Override
    public boolean protectedAsset(byte[] spaceId, byte[] assetId) {
        return Boolean.TRUE.equals(mapper.protectedAsset(spaceId, assetId));
    }

    @Override
    public List<byte[]> protectedAssets(byte[] spaceId, List<byte[]> assetIds) {
        return mapper.protectedAssets(spaceId, assetIds);
    }

    private AccessDecision decision(MediaAccessMapper.AccessRow row) {
        return row == null ? null : new AccessDecision(row.canAccess(), row.protectedContent());
    }
}
