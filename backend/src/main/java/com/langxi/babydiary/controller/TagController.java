package com.langxi.babydiary.controller;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.TagCreateDTO;
import com.langxi.babydiary.dto.TagVO;
import com.langxi.babydiary.entity.Tag;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tags")
public class TagController {
    @Autowired
    private TagService tagService;

    @Autowired
    private CurrentUser currentUser;

    @GetMapping
    public Result<List<TagVO>> listTags() {
        List<TagVO> tags = tagService.findTagsByUserId(currentUser.getUserId())
                .stream()
                .map(TagVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(tags);
    }

    @PostMapping
    public Result<TagVO> createTag(@Valid @RequestBody TagCreateDTO payload) {
        Tag tag = tagService.createTag(
                currentUser.getUserId(),
                payload.getName(),
                payload.getColor()
        );
        return Result.success("创建成功", TagVO.fromEntity(tag));
    }
}
