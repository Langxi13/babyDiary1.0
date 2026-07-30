package com.langxi.babydiary.media.application;

import java.util.List;

public interface MediaAccessRepository {
    AccessDecision direct(byte[] spaceId, byte[] assetId, long accountId);

    AccessDecision diary(byte[] spaceId, byte[] assetId, byte[] diaryId, long accountId);

    AccessDecision album(byte[] spaceId, byte[] assetId, byte[] albumId, long accountId);

    AccessDecision anniversary(
            byte[] spaceId, byte[] assetId, byte[] anniversaryId, long accountId);

    AccessDecision avatar(byte[] spaceId, byte[] assetId, byte[] accountId);

    AccessDecision share(byte[] spaceId, byte[] assetId, byte[] shareId);

    boolean protectedAsset(byte[] spaceId, byte[] assetId);

    List<byte[]> protectedAssets(byte[] spaceId, List<byte[]> assetIds);

    record AccessDecision(boolean canAccess, boolean protectedContent) {}
}
