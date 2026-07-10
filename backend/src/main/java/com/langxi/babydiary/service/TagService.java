package com.langxi.babydiary.service;

import com.langxi.babydiary.common.CacheNames;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.DiaryTagRow;
import com.langxi.babydiary.entity.Tag;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.TagMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TagService {
    @Autowired
    private TagMapper tagMapper;

    @Cacheable(cacheNames = CacheNames.TAGS, key = "'user:' + #userId")
    public List<Tag> findTagsByUserId(Integer userId) {
        return tagMapper.findTagsByUserId(userId);
    }

    public List<Tag> findTagsByDiaryId(Integer diaryId) {
        return tagMapper.findTagsByDiaryId(diaryId);
    }

    public Map<Integer, List<Tag>> findTagsByDiaryIds(List<Integer> diaryIds) {
        if (diaryIds == null || diaryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, List<Tag>> grouped = tagMapper.findTagsByDiaryIds(diaryIds)
                .stream()
                .collect(Collectors.groupingBy(
                        DiaryTagRow::getDiaryId,
                        Collectors.mapping(DiaryTagRow::toTag, Collectors.toList())));
        Map<Integer, List<Tag>> result = new HashMap<>();
        for (Integer diaryId : diaryIds) {
            result.put(diaryId, grouped.getOrDefault(diaryId, Collections.emptyList()));
        }
        return result;
    }

    @CacheEvict(cacheNames = {CacheNames.TAGS, CacheNames.DIARY_PAGE, CacheNames.DIARY_TIMELINE}, allEntries = true)
    public Tag createTag(Integer userId, String name, String color) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名不能为空");
        }
        if (normalizedName.length() > 32) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名不能超过32个字符");
        }
        Tag existing = tagMapper.findTagByName(userId, normalizedName);
        if (existing != null) {
            return existing;
        }
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(normalizedName);
        tag.setColor(normalizeColor(color));
        tagMapper.insertTag(tag);
        return tag;
    }

    @CacheEvict(cacheNames = {CacheNames.DIARY_PAGE, CacheNames.DIARY_TIMELINE}, allEntries = true)
    public void replaceDiaryTags(Integer userId, Integer diaryId, List<Integer> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            tagMapper.deleteDiaryTagsByDiaryId(diaryId);
            return;
        }
        Set<Integer> uniqueTagIds = tagIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueTagIds.size() > 50) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单篇日记最多选择50个标签");
        }
        for (Integer tagId : uniqueTagIds) {
            if (tagMapper.findTagById(userId, tagId) == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "标签不存在: " + tagId);
            }
        }
        tagMapper.deleteDiaryTagsByDiaryId(diaryId);
        if (!uniqueTagIds.isEmpty()) {
            tagMapper.insertDiaryTags(diaryId, List.copyOf(uniqueTagIds));
        }
    }

    private String normalizeColor(String color) {
        String normalized = color == null || color.trim().isEmpty() ? "#f56c6c" : color.trim();
        if (!normalized.matches("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签颜色格式无效");
        }
        return normalized;
    }
}
