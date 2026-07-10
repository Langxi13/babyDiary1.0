package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.Tag;
import lombok.Data;

@Data
public class TagVO {
    private Integer tagId;
    private String name;
    private String color;

    public static TagVO fromEntity(Tag tag) {
        TagVO vo = new TagVO();
        vo.setTagId(tag.getTagId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        return vo;
    }
}
