package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.DiaryTemplate;
import lombok.Data;

@Data
public class DiaryTemplateVO {
    private String templateId;
    private String name;
    private String description;
    private String icon;
    private String promptText;
    private String contentHtml;
    private Boolean builtin;
    private Boolean editable;

    public static DiaryTemplateVO from(DiaryTemplate template, Integer userId) {
        DiaryTemplateVO vo = new DiaryTemplateVO();
        vo.setTemplateId(template.getPublicId());
        vo.setName(template.getName());
        vo.setDescription(template.getDescription());
        vo.setIcon(template.getIcon());
        vo.setPromptText(template.getPromptText());
        vo.setContentHtml(template.getContentHtml());
        vo.setBuiltin(template.getBuiltin());
        vo.setEditable(!Boolean.TRUE.equals(template.getBuiltin()) && userId.equals(template.getOwnerUserId()));
        return vo;
    }
}
