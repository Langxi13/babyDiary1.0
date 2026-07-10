package com.langxi.babydiary.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void mapPreservesPaginationMetadata() {
        PageResult<Integer> source = new PageResult<>(Arrays.asList(1, 2), 1, 2, 5L);

        PageResult<String> mapped = source.map(value -> "item-" + value);

        assertThat(mapped.getContent()).containsExactly("item-1", "item-2");
        assertThat(mapped.getPageNumber()).isEqualTo(1);
        assertThat(mapped.getPageSize()).isEqualTo(2);
        assertThat(mapped.getTotalElements()).isEqualTo(5);
        assertThat(mapped.getTotalPages()).isEqualTo(3);
    }
}
