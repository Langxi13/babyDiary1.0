package com.langxi.babydiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DiaryCommentDTO {
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论不能超过2000个字符")
    private String content;
}
