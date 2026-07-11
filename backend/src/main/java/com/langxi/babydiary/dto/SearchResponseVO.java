package com.langxi.babydiary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SearchResponseVO {
    private String query;
    private boolean semanticEnabled;
    private List<SearchResultVO> results;
}
