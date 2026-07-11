package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.DiaryTemplateDTO;
import com.langxi.babydiary.dto.DiaryTemplateVO;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.entity.DiaryTemplate;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.DiaryTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DiaryTemplateService {
    private final DiaryTemplateMapper mapper;
    private final SpaceService spaceService;
    private final HtmlSanitizer htmlSanitizer;

    public DiaryTemplateService(DiaryTemplateMapper mapper, SpaceService spaceService, HtmlSanitizer htmlSanitizer) {
        this.mapper = mapper;
        this.spaceService = spaceService;
        this.htmlSanitizer = htmlSanitizer;
    }

    public List<DiaryTemplateVO> list(String spacePublicId, Integer userId) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        return mapper.list(space.getSpaceId()).stream().map(template -> DiaryTemplateVO.from(template, userId)).toList();
    }

    @Transactional
    public DiaryTemplateVO create(String spacePublicId, Integer userId, DiaryTemplateDTO dto) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        DiaryTemplate template = new DiaryTemplate();
        template.setPublicId(UUID.randomUUID().toString());
        template.setSpaceId(space.getSpaceId());
        template.setOwnerUserId(userId);
        apply(template, dto);
        mapper.insert(template);
        template.setBuiltin(false);
        return DiaryTemplateVO.from(template, userId);
    }

    @Transactional
    public DiaryTemplateVO update(String spacePublicId, String publicId, Integer userId, DiaryTemplateDTO dto) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        DiaryTemplate template = requireCustom(space, publicId);
        template.setOwnerUserId(userId);
        apply(template, dto);
        if (mapper.update(template) != 1) throw new BusinessException(ErrorCode.FORBIDDEN);
        template.setBuiltin(false);
        return DiaryTemplateVO.from(template, userId);
    }

    @Transactional
    public void delete(String spacePublicId, String publicId, Integer userId) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        DiaryTemplate template = requireCustom(space, publicId);
        if (mapper.deactivate(template.getTemplateId(), userId) != 1) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private DiaryTemplate requireCustom(DiarySpace space, String publicId) {
        DiaryTemplate template = mapper.findByPublicId(publicId);
        if (template == null || Boolean.TRUE.equals(template.getBuiltin()) || !space.getSpaceId().equals(template.getSpaceId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "模板不存在");
        }
        return template;
    }

    private void apply(DiaryTemplate template, DiaryTemplateDTO dto) {
        template.setName(dto.getName().trim());
        template.setDescription(dto.getDescription() == null ? null : dto.getDescription().trim());
        template.setIcon(dto.getIcon() == null ? "Notebook" : dto.getIcon().trim());
        template.setPromptText(dto.getPromptText() == null ? null : dto.getPromptText().trim());
        template.setContentHtml(htmlSanitizer.sanitize(dto.getContentHtml()));
    }
}
