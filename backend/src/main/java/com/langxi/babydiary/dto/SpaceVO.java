package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.DiarySpace;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class SpaceVO {
    private String spaceId;
    private String name;
    private String type;
    private String role;
    private int memberCount;
    private Timestamp createdAt;

    public static SpaceVO from(DiarySpace space, String role, int memberCount) {
        SpaceVO vo = new SpaceVO();
        vo.setSpaceId(space.getPublicId());
        vo.setName(space.getName());
        vo.setType(space.getType());
        vo.setRole(role);
        vo.setMemberCount(memberCount);
        vo.setCreatedAt(space.getCreatedAt());
        return vo;
    }
}
