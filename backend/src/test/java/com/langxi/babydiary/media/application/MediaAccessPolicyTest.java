package com.langxi.babydiary.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MediaAccessPolicyTest {

    @Test
    void protectedAssetLookupUsesBoundedBatches() {
        MediaAccessRepository repository = mock(MediaAccessRepository.class);
        when(repository.protectedAssets(any(byte[].class), any())).thenReturn(List.of());
        MediaAccessPolicy policy = new MediaAccessPolicy(repository);
        List<UUID> ids =
                java.util.stream.IntStream.range(0, 501)
                        .mapToObj(ignored -> UUID.randomUUID())
                        .toList();

        assertThat(policy.protectedAssets(UUID.randomUUID(), ids)).isEmpty();
        verify(repository, times(2)).protectedAssets(any(byte[].class), any());
    }
}
