package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.AlbumGroup;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AlbumGroupVO {
    private Integer groupId;
    private String type;
    private String name;
    private Boolean editable;
    private List<AlbumVO> albums = new ArrayList<>();

    public static AlbumGroupVO system(List<AlbumVO> albums) {
        AlbumGroupVO vo = new AlbumGroupVO();
        vo.setType("SYSTEM");
        vo.setName("默认相册");
        vo.setEditable(false);
        vo.setAlbums(albums);
        return vo;
    }

    public static AlbumGroupVO fromEntity(AlbumGroup group, List<AlbumVO> albums) {
        AlbumGroupVO vo = new AlbumGroupVO();
        vo.setGroupId(group.getGroupId());
        vo.setType(group.getType());
        vo.setName(group.getName());
        vo.setEditable("CUSTOM".equals(group.getType()));
        vo.setAlbums(albums);
        return vo;
    }
}
