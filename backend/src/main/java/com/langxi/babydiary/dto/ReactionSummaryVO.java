package com.langxi.babydiary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ReactionSummaryVO {
    private String emoji;
    private Integer count;
    private Boolean reactedByMe;
    private List<String> usernames;
}
