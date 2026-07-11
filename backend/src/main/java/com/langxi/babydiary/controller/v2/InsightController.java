package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.InsightVO;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.InsightService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v2/spaces/{spaceId}/insights")
public class InsightController {
    private final InsightService insightService;
    private final CurrentUser currentUser;

    public InsightController(InsightService insightService, CurrentUser currentUser) {
        this.insightService = insightService;
        this.currentUser = currentUser;
    }

    @GetMapping("/yearly")
    public Result<InsightVO> yearly(@PathVariable String spaceId,
                                    @RequestParam(required = false) Integer year) {
        int selectedYear = year == null ? LocalDate.now().getYear() : year;
        return Result.success(insightService.yearly(spaceId, currentUser.getUserId(), selectedYear));
    }
}
