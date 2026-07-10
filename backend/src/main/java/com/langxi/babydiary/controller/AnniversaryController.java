package com.langxi.babydiary.controller;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.AnniversaryCoverUploadVO;
import com.langxi.babydiary.dto.AnniversaryDTO;
import com.langxi.babydiary.dto.AnniversaryVO;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.AnniversaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/anniversaries")
public class AnniversaryController {
    @Autowired
    private AnniversaryService anniversaryService;

    @Autowired
    private CurrentUser currentUser;

    @GetMapping
    public Result<List<AnniversaryVO>> listAnniversaries() {
        List<AnniversaryVO> anniversaries = anniversaryService.findByUserId(currentUser.getUserId())
                .stream()
                .map(AnniversaryVO::fromEntity)
                .collect(Collectors.toList());
        return Result.success(anniversaries);
    }

    @PostMapping
    public Result<AnniversaryVO> createAnniversary(@Valid @RequestBody AnniversaryDTO dto) {
        return Result.success("创建成功", AnniversaryVO.fromEntity(anniversaryService.create(
                currentUser.getUserId(),
                dto.getTitle(),
                dto.getDate(),
                dto.getDescription(),
                dto.getCoverImagePath(),
                dto.getSort()
        )));
    }

    @PostMapping("/cover")
    public Result<AnniversaryCoverUploadVO> uploadCover(@RequestParam("coverFile") MultipartFile coverFile) {
        String coverImagePath = anniversaryService.uploadCover(currentUser.getUserId(), coverFile);
        return Result.success("上传成功", new AnniversaryCoverUploadVO(coverImagePath));
    }

    @PutMapping("/{anniversaryId}")
    public Result<AnniversaryVO> updateAnniversary(
            @PathVariable Integer anniversaryId,
            @Valid @RequestBody AnniversaryDTO dto) {
        return Result.success("更新成功", AnniversaryVO.fromEntity(anniversaryService.update(
                currentUser.getUserId(),
                anniversaryId,
                dto.getTitle(),
                dto.getDate(),
                dto.getDescription(),
                dto.getCoverImagePath(),
                dto.getSort()
        )));
    }

    @DeleteMapping("/{anniversaryId}")
    public Result<Void> deleteAnniversary(@PathVariable Integer anniversaryId) {
        anniversaryService.delete(currentUser.getUserId(), anniversaryId);
        return Result.success("删除成功", null);
    }
}
