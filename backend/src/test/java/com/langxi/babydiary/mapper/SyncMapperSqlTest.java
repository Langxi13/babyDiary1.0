package com.langxi.babydiary.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SyncMapperSqlTest {

    @Test
    void changeCursorAliasIsEscapedForMySql() throws Exception {
        Select select = Arrays.stream(SyncMapper.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("findChanges"))
                .findFirst()
                .orElseThrow()
                .getAnnotation(Select.class);

        assertThat(String.join(" ", select.value())).contains("AS `cursor`");
    }
}
