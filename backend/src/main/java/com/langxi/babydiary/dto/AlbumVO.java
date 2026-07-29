package com.langxi.babydiary.dto;

import com.langxi.babydiary.entity.Album;
import lombok.Data;

@Data
public class AlbumVO {
    private Integer albumId;
    private String systemKey;
    private String type;
    private String name;
    private String description;
    private MediaAssetVO coverMedia;
    private String coverAssetId;
    private Integer photoCount;
    private Boolean editable;

    public static AlbumVO fromEntity(Album album) {
        AlbumVO vo = new AlbumVO();
        vo.setAlbumId(album.getAlbumId());
        vo.setType(album.getType());
        vo.setName(album.getName());
        vo.setDescription(album.getDescription());
        vo.setCoverAssetId(album.getCoverAssetPublicId());
        vo.setPhotoCount(album.getPhotoCount() == null ? 0 : album.getPhotoCount());
        vo.setEditable(!"SYSTEM".equals(album.getType()));
        return vo;
    }

    public static AlbumVO system(String systemKey, String name, Integer photoCount, String coverAssetId) {
        AlbumVO vo = new AlbumVO();
        vo.setSystemKey(systemKey);
        vo.setType("SYSTEM");
        vo.setName(name);
        vo.setCoverAssetId(coverAssetId);
        vo.setPhotoCount(photoCount == null ? 0 : photoCount);
        vo.setEditable(false);
        return vo;
    }
}
