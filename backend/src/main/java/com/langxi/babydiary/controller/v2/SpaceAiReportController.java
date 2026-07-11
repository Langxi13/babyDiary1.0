package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.PageResult;
import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.*;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.SpaceAiReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/spaces/{spaceId}/ai")
public class SpaceAiReportController {
    private final SpaceAiReportService reportService;
    private final CurrentUser currentUser;

    public SpaceAiReportController(SpaceAiReportService reportService, CurrentUser currentUser) {
        this.reportService = reportService;
        this.currentUser = currentUser;
    }

    @PostMapping("/reports")
    public Result<AiReportVO> generate(@PathVariable String spaceId,
                                       @Valid @RequestBody AiReportGenerateDTO dto) {
        return Result.success("报告已生成", reportService.generate(spaceId, currentUser.getUserId(), dto));
    }

    @GetMapping("/reports")
    public Result<PageResult<AiReportVO>> list(@PathVariable String spaceId,
                                               @RequestParam(required = false) String type,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return Result.success(reportService.list(spaceId, currentUser.getUserId(), type, page, size));
    }

    @GetMapping("/reports/{reportId}")
    public Result<AiReportVO> report(@PathVariable String spaceId, @PathVariable Integer reportId) {
        return Result.success(reportService.find(spaceId, currentUser.getUserId(), reportId));
    }

    @GetMapping("/schedule")
    public Result<SpaceAiScheduleVO> schedule(@PathVariable String spaceId) {
        return Result.success(reportService.schedule(spaceId, currentUser.getUserId()));
    }

    @PutMapping("/schedule")
    public Result<SpaceAiScheduleVO> updateSchedule(@PathVariable String spaceId,
                                                    @RequestBody SpaceAiScheduleDTO dto) {
        return Result.success("自动报告计划已更新", reportService.updateSchedule(spaceId, currentUser.getUserId(), dto));
    }
}
