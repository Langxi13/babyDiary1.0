package com.langxi.babydiary.home.application;

public interface HomeProjectionRepository {
    HomeProjection load(long spaceId, long accountId, boolean elevated);
}
