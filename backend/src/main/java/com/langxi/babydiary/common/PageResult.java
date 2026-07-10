package com.langxi.babydiary.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Schema(description = "分页响应结果")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "数据列表")
    private List<T> content;

    @Schema(description = "当前页码", example = "0")
    private Integer pageNumber;

    @Schema(description = "每页大小", example = "5")
    private Integer pageSize;

    @Schema(description = "总记录数", example = "100")
    private Long totalElements;

    @Schema(description = "总页数", example = "20")
    private Integer totalPages;

    @Schema(description = "是否首页", example = "true")
    private Boolean first;

    @Schema(description = "是否末页", example = "false")
    private Boolean last;

    public PageResult() {
    }

    public PageResult(List<T> content, Integer pageNumber, Integer pageSize, Long totalElements) {
        this.content = content;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
        this.first = pageNumber == 0;
        this.last = pageNumber >= totalPages - 1;
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mappedContent = content == null
                ? Collections.emptyList()
                : content.stream().map(mapper).collect(Collectors.toList());
        return new PageResult<>(mappedContent, pageNumber, pageSize, totalElements);
    }
}
