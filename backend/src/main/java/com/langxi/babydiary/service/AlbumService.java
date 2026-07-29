package com.langxi.babydiary.service;

import com.langxi.babydiary.common.CacheNames;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Pagination;
import com.langxi.babydiary.dto.AlbumDTO;
import com.langxi.babydiary.dto.AlbumGroupDTO;
import com.langxi.babydiary.dto.AlbumGroupVO;
import com.langxi.babydiary.dto.AlbumVO;
import com.langxi.babydiary.entity.Album;
import com.langxi.babydiary.entity.AlbumGroup;
import com.langxi.babydiary.entity.Photo;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AlbumMapper;
import com.langxi.babydiary.mapper.PhotoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AlbumService {

    @Autowired
    private AlbumMapper albumMapper;

    @Autowired
    private PhotoMapper photoMapper;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private SpaceService spaceService;

    @Cacheable(cacheNames = CacheNames.PHOTOS, key = "'album-groups:' + #userId")
    public List<AlbumGroupVO> listGroups(Integer userId) {
        ensureAiGroup(userId);
        List<AlbumGroupVO> result = new ArrayList<>();
        result.add(systemGroup(userId));

        List<AlbumGroup> groups = albumMapper.findGroupsByUserId(userId);
        List<Integer> groupIds = groups.stream().map(AlbumGroup::getGroupId).collect(Collectors.toList());
        Map<Integer, List<AlbumVO>> albumsByGroup = new HashMap<>();
        if (!groupIds.isEmpty()) {
            for (Album album : albumMapper.findAlbumsByGroupIds(groupIds)) {
                albumsByGroup.computeIfAbsent(album.getGroupId(), key -> new ArrayList<>()).add(enrichAlbum(AlbumVO.fromEntity(album)));
            }
        }
        for (AlbumGroup group : groups) {
            result.add(AlbumGroupVO.fromEntity(group, albumsByGroup.getOrDefault(group.getGroupId(), Collections.emptyList())));
        }
        return result;
    }

    public List<Photo> findSystemPhotos(Integer userId, String systemKey) {
        if ("all".equals(systemKey)) {
            return photoService.findPhotos(userId, null, null, null, null, null);
        }
        if ("favorites".equals(systemKey)) {
            return photoService.findPhotos(userId, null, null, null, null, true);
        }
        if (systemKey != null && systemKey.startsWith("year:")) {
            String year = systemKey.substring("year:".length());
            return photoService.findPhotos(userId, year + "-01-01", year + "-12-31", null, null, null);
        }
        throw new BusinessException(ErrorCode.ALBUM_NOT_FOUND);
    }

    public List<Photo> findAlbumPhotos(Integer userId, Integer albumId) {
        requireEditableOrExisting(userId, albumId);
        return photoService.enrichMedia(albumMapper.findAlbumPhotos(userId, albumId));
    }

    public PageResult<Photo> findSystemPhotoPage(Integer userId, String systemKey, int page, int size) {
        String startDate = null;
        String endDate = null;
        Boolean favoriteOnly = null;
        if ("favorites".equals(systemKey)) {
            favoriteOnly = true;
        } else if (systemKey != null && systemKey.startsWith("year:")) {
            String year = systemKey.substring("year:".length());
            startDate = year + "-01-01";
            endDate = year + "-12-31";
        } else if (!"all".equals(systemKey)) {
            throw new BusinessException(ErrorCode.ALBUM_NOT_FOUND);
        }
        return photoService.findPhotoPage(userId, startDate, endDate, null, null, favoriteOnly, page, size);
    }

    @Cacheable(cacheNames = CacheNames.PHOTOS, key = "'album-page:' + #userId + ':' + #albumId + ':' + #page + ':' + #size")
    public PageResult<Photo> findAlbumPhotoPage(Integer userId, Integer albumId, int page, int size) {
        requireEditableOrExisting(userId, albumId);
        int normalizedPage = Pagination.normalizePage(page);
        int normalizedSize = Pagination.normalizeSize(size);
        long total = albumMapper.countAlbumPhotos(userId, albumId);
        List<Photo> content = total == 0
                ? Collections.emptyList()
                : albumMapper.findAlbumPhotoPage(
                        userId,
                        albumId,
                        normalizedSize,
                        Pagination.offset(normalizedPage, normalizedSize));
        return new PageResult<>(photoService.enrichMedia(content), normalizedPage, normalizedSize, total);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public AlbumGroup createGroup(Integer userId, AlbumGroupDTO dto) {
        AlbumGroup group = new AlbumGroup();
        group.setUserId(userId);
        group.setType("CUSTOM");
        group.setName(trimRequired(dto.getName()));
        group.setSort(20);
        albumMapper.insertGroup(group);
        return group;
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public AlbumGroup updateGroup(Integer userId, Integer groupId, AlbumGroupDTO dto) {
        AlbumGroup group = requireGroup(userId, groupId);
        if (!"CUSTOM".equals(group.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统相册组不可编辑");
        }
        group.setName(trimRequired(dto.getName()));
        albumMapper.updateGroup(group);
        return group;
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public void deleteGroup(Integer userId, Integer groupId) {
        AlbumGroup group = requireGroup(userId, groupId);
        if (!"CUSTOM".equals(group.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统相册组不可编辑");
        }
        albumMapper.deleteGroup(userId, groupId);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public Album createAlbum(Integer userId, AlbumDTO dto) {
        AlbumGroup group = requireGroup(userId, dto.getGroupId());
        if (!"CUSTOM".equals(group.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能在自建相册组中新建手动相册");
        }
        Album album = new Album();
        album.setUserId(userId);
        album.setGroupId(group.getGroupId());
        album.setType("CUSTOM");
        album.setName(trimRequired(dto.getName()));
        album.setDescription(trimToNull(dto.getDescription()));
        album.setCoverAssetId(resolveCoverAssetId(userId, dto.getCoverAssetId()));
        album.setSort(0);
        albumMapper.insertAlbum(album);
        return albumMapper.findAlbumById(userId, album.getAlbumId());
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public Album updateAlbum(Integer userId, Integer albumId, AlbumDTO dto) {
        Album album = requireEditableAlbum(userId, albumId);
        album.setName(trimRequired(dto.getName()));
        album.setDescription(trimToNull(dto.getDescription()));
        album.setCoverAssetId(resolveCoverAssetId(userId, dto.getCoverAssetId()));
        albumMapper.updateAlbum(album);
        return albumMapper.findAlbumById(userId, albumId);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public void deleteAlbum(Integer userId, Integer albumId) {
        requireEditableAlbum(userId, albumId);
        albumMapper.deleteAlbum(userId, albumId);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public void addPhotos(Integer userId, Integer albumId, List<?> assetIds) {
        requireEditableAlbum(userId, albumId);
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        List<?> uniqueAssetIds = assetIds.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (uniqueAssetIds.isEmpty()) {
            return;
        }
        List<Photo> ownedPhotos = photoMapper.findPhotosByIds(userId, uniqueAssetIds);
        List<Photo> safePhotos = ownedPhotos == null ? Collections.emptyList() : ownedPhotos;
        java.util.Set<String> ownedIds = safePhotos.stream()
                .map(Photo::getAssetId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        boolean allOwned = uniqueAssetIds.stream()
                .map(String::valueOf)
                .allMatch(ownedIds::contains);
        if (!allOwned) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "部分照片不存在或不属于当前用户");
        }
        int sort = albumMapper.nextAlbumPhotoSort(albumId);
        for (Object assetId : uniqueAssetIds) {
            albumMapper.insertAlbumPhoto(albumId, userId, assetId, sort++);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public void removePhoto(Integer userId, Integer albumId, String assetId) {
        requireEditableAlbum(userId, albumId);
        albumMapper.deleteAlbumPhoto(albumId, assetId);
    }

    AlbumGroup ensureAiGroup(Integer userId) {
        AlbumGroup group = albumMapper.ensureAiGroup(userId);
        if (group != null) {
            return group;
        }
        AlbumGroup created = new AlbumGroup();
        created.setUserId(userId);
        created.setType("AI");
        created.setName("AI 整理");
        created.setSort(10);
        albumMapper.insertGroup(created);
        return created;
    }

    private AlbumGroupVO systemGroup(Integer userId) {
        List<AlbumVO> albums = new ArrayList<>();
        albums.add(enrichAlbum(AlbumVO.system(
                "all",
                "所有图片",
                photoMapper.countPhotos(userId, null, null, null, null, null),
                photoMapper.findCoverAssetPublicId(userId, null, null, null)
        )));
        albums.add(enrichAlbum(AlbumVO.system(
                "favorites",
                "收藏照片",
                photoMapper.countPhotos(userId, null, null, null, null, true),
                photoMapper.findCoverAssetPublicId(userId, null, null, true)
        )));
        for (Integer year : photoMapper.findPhotoYears(userId)) {
            String startDate = year + "-01-01";
            String endDate = year + "-12-31";
            albums.add(enrichAlbum(AlbumVO.system(
                    "year:" + year,
                    year + " 年",
                    photoMapper.countPhotos(userId, startDate, endDate, null, null, null),
                    photoMapper.findCoverAssetPublicId(userId, startDate, endDate, null)
            )));
        }
        return AlbumGroupVO.system(albums);
    }

    private AlbumGroup requireGroup(Integer userId, Integer groupId) {
        AlbumGroup group = albumMapper.findGroupById(userId, groupId);
        if (group == null) {
            throw new BusinessException(ErrorCode.ALBUM_GROUP_NOT_FOUND);
        }
        return group;
    }

    private Album requireEditableAlbum(Integer userId, Integer albumId) {
        Album album = requireEditableOrExisting(userId, albumId);
        if ("SYSTEM".equals(album.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统相册不可编辑");
        }
        return album;
    }

    private Album requireEditableOrExisting(Integer userId, Integer albumId) {
        Album album = albumMapper.findAlbumById(userId, albumId);
        if (album == null) {
            throw new BusinessException(ErrorCode.ALBUM_NOT_FOUND);
        }
        return album;
    }

    private Long resolveCoverAssetId(Integer userId, String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        if (mediaService == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "封面媒体资源无效");
        return mediaService.requireOwnedAsset(
                spaceService.requirePersonalSpace(userId).getPublicId(), publicId, userId).getAssetId();
    }

    private AlbumVO enrichAlbum(AlbumVO vo) {
        if (mediaService != null && vo.getCoverAssetId() != null) {
            var asset = mediaService.findByPublicId(vo.getCoverAssetId());
            if (asset != null) vo.setCoverMedia(mediaService.toVO(asset));
        }
        return vo;
    }

    public AlbumVO toVO(Album album) {
        return enrichAlbum(AlbumVO.fromEntity(album));
    }


    private String trimRequired(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "名称不能为空");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
