package com.langxi.babydiary.media.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MyBatisMediaRepositoryTest {

    @Test
    void publicIdLookupUsesBoundedBatches() {
        MediaMapper mapper = mock(MediaMapper.class);
        when(mapper.findByPublicIdsInSpace(eq(11L), anyList())).thenReturn(List.of());
        MyBatisMediaRepository repository = new MyBatisMediaRepository(mapper);
        List<UUID> ids =
                java.util.stream.IntStream.range(0, 501)
                        .mapToObj(ignored -> UUID.randomUUID())
                        .toList();

        assertThat(repository.findByPublicIdsInSpace(11L, ids)).isEmpty();
        verify(mapper, times(2)).findByPublicIdsInSpace(eq(11L), anyList());
    }
}
