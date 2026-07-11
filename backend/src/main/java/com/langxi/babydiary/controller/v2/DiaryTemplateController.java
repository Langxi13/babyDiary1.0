package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.DiaryTemplateDTO;
import com.langxi.babydiary.dto.DiaryTemplateVO;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.DiaryTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/spaces/{spaceId}/templates")
public class DiaryTemplateController {
    private final DiaryTemplateService templateService;
    private final CurrentUser currentUser;

    public DiaryTemplateController(DiaryTemplateService templateService, CurrentUser currentUser) {
        this.templateService = templateService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Result<List<DiaryTemplateVO>> list(@PathVariable String spaceId) {
        return Result.success(templateService.list(spaceId, currentUser.getUserId()));
    }

    @PostMapping
    public Result<DiaryTemplateVO> create(@PathVariable String spaceId,
                                           @Valid @RequestBody DiaryTemplateDTO dto) {
        return Result.success("模板已创建", templateService.create(spaceId, currentUser.getUserId(), dto));
    }

    @PutMapping("/{templateId}")
    public Result<DiaryTemplateVO> update(@PathVariable String spaceId,
                                           @PathVariable String templateId,
                                           @Valid @RequestBody DiaryTemplateDTO dto) {
        return Result.success("模板已更新", templateService.update(spaceId, templateId, currentUser.getUserId(), dto));
    }

    @DeleteMapping("/{templateId}")
    public Result<Void> delete(@PathVariable String spaceId, @PathVariable String templateId) {
        templateService.delete(spaceId, templateId, currentUser.getUserId());
        return Result.success("模板已删除", null);
    }
}
