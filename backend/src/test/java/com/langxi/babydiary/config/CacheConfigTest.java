package com.langxi.babydiary.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheConfigTest {

    @Test
    void cacheFailuresAreSwallowedSoDatabaseRequestsCanContinue() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("diaryPage");
        CacheErrorHandler handler = new CacheConfig().errorHandler();
        RuntimeException failure = new RuntimeException("redis unavailable");

        assertThatCode(() -> handler.handleCacheGetError(failure, cache, "key")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCachePutError(failure, cache, "key", "value")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheEvictError(failure, cache, "key")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheClearError(failure, cache)).doesNotThrowAnyException();
    }
}
