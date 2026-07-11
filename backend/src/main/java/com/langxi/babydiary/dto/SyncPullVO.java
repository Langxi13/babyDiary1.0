package com.langxi.babydiary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SyncPullVO {
    private Long nextCursor;
    private boolean hasMore;
    private List<SyncChangeVO> changes;
}
