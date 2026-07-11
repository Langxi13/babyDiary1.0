package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.SpaceMember;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class SpaceMemberVO {
    private Integer userId;
    private String username;
    private String avatarPath;
    private String role;
    private Timestamp joinedAt;

    public static SpaceMemberVO from(SpaceMember member) {
        SpaceMemberVO vo = new SpaceMemberVO();
        vo.setUserId(member.getUserId());
        vo.setUsername(member.getUsername());
        vo.setAvatarPath(member.getAvatarPath());
        vo.setRole(member.getRole());
        vo.setJoinedAt(member.getJoinedAt());
        return vo;
    }
}
