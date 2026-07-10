package com.langxi.babydiary.controller;

import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.AiConfigDTO;
import com.langxi.babydiary.dto.AiConfigVO;
import com.langxi.babydiary.dto.AiAlbumProposalRequestDTO;
import com.langxi.babydiary.dto.AiAlbumProposalVO;
import com.langxi.babydiary.dto.AiReportGenerateDTO;
import com.langxi.babydiary.dto.AiReportVO;
import com.langxi.babydiary.entity.AiReport;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.AiConfigService;
import com.langxi.babydiary.service.AiAlbumProposalService;
import com.langxi.babydiary.service.AiReportService;
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

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private AiReportService aiReportService;

    @Autowired
    private AiAlbumProposalService aiAlbumProposalService;

    @Autowired
    private CurrentUser currentUser;

    @GetMapping("/config")
    public Result<AiConfigVO> getConfig() {
        return Result.success(aiConfigService.getConfig());
    }

    @PutMapping("/config")
    public Result<AiConfigVO> saveConfig(@Valid @RequestBody AiConfigDTO dto) {
        return Result.success("AI配置已保存", aiConfigService.saveConfig(dto));
    }

    @PostMapping("/config/test")
    public Result<String> testConfig() {
        return Result.success("连接成功", aiConfigService.testConnection());
    }

    @GetMapping("/models")
    public Result<List<String>> listModels() {
        return Result.success(aiConfigService.listModels());
    }

    @PostMapping("/reports/generate")
    public Result<AiReportVO> generateReport(@Valid @RequestBody AiReportGenerateDTO dto) {
        return Result.success("报告已生成", AiReportVO.fromEntity(aiReportService.generate(currentUser.getUserId(), dto)));
    }

    @GetMapping("/reports")
    public Result<PageResult<AiReportVO>> listReports(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageResult<AiReport> reports = aiReportService.findReports(currentUser.getUserId(), type, page, size);
        return Result.success(reports.map(AiReportVO::fromEntity));
    }

    @GetMapping("/reports/{reportId}")
    public Result<AiReportVO> getReport(@PathVariable Integer reportId) {
        return Result.success(AiReportVO.fromEntity(aiReportService.findById(currentUser.getUserId(), reportId)));
    }

    @DeleteMapping("/reports/{reportId}")
    public Result<Void> deleteReport(@PathVariable Integer reportId) {
        aiReportService.delete(currentUser.getUserId(), reportId);
        return Result.success("报告已删除", null);
    }

    @PostMapping("/albums/proposals")
    public Result<AiAlbumProposalVO> generateAlbumProposal(@Valid @RequestBody AiAlbumProposalRequestDTO dto) {
        return Result.success("AI相册推荐已生成", aiAlbumProposalService.generate(currentUser.getUserId(), dto));
    }

    @GetMapping("/albums/proposals/{proposalId}")
    public Result<AiAlbumProposalVO> getAlbumProposal(@PathVariable Integer proposalId) {
        return Result.success(aiAlbumProposalService.findById(currentUser.getUserId(), proposalId));
    }

    @PutMapping("/albums/proposals/{proposalId}")
    public Result<AiAlbumProposalVO> updateAlbumProposal(@PathVariable Integer proposalId, @Valid @RequestBody AiAlbumProposalVO dto) {
        return Result.success("AI相册推荐已更新", aiAlbumProposalService.update(currentUser.getUserId(), proposalId, dto));
    }

    @PostMapping("/albums/proposals/{proposalId}/confirm")
    public Result<AiAlbumProposalVO> confirmAlbumProposal(@PathVariable Integer proposalId) {
        return Result.success("AI相册已保存", aiAlbumProposalService.confirm(currentUser.getUserId(), proposalId));
    }

    @DeleteMapping("/albums/proposals/{proposalId}")
    public Result<Void> discardAlbumProposal(@PathVariable Integer proposalId) {
        aiAlbumProposalService.discard(currentUser.getUserId(), proposalId);
        return Result.success("AI相册推荐已放弃", null);
    }
}
