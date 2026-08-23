package com.langxi.babydiary.home.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.langxi.babydiary.platform.application.ReadCache;
import com.langxi.babydiary.platform.application.ReadCacheInvalidator;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class HomeService {
    private static final TypeReference<HomeProjection> HOME = new TypeReference<>() {};
    private final SpaceAccess spaces;
    private final HomeProjectionRepository projections;
    private final ReadCache cache;

    public HomeService(SpaceAccess spaces, HomeProjectionRepository projections, ReadCache cache) {
        this.spaces = spaces;
        this.projections = projections;
        this.cache = cache;
    }

    public HomeProjection home(UUID spaceId, long accountId, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        if (elevated) return projections.load(space.internalId(), accountId, true);
        return cache.get(
                ReadCacheInvalidator.HOME,
                spaceId,
                accountId,
                "projection",
                Duration.ofMinutes(2),
                HOME,
                () -> projections.load(space.internalId(), accountId, false));
    }
}
