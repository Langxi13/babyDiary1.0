package com.langxi.babydiary.diary.api;

import com.langxi.babydiary.diary.application.DiaryReadService;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.StepUpService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/diaries")
public class DiaryReadController {
    private final DiaryReadService reads;
    private final StepUpService stepUp;

    public DiaryReadController(DiaryReadService reads,StepUpService stepUp) {
        this.reads = reads;
        this.stepUp=stepUp;
    }

    @GetMapping("/calendar")
    public DiaryReadService.CalendarMonth calendar(@AuthenticationPrincipal AccountPrincipal principal,
                                                    @PathVariable UUID spaceId,
                                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                                    @RequestHeader(value="X-Step-Up-Token",required=false)String token) {
        return reads.calendar(spaceId, principal.accountId(), month,stepUp.valid(principal,token));
    }

    @GetMapping("/timeline")
    public DiaryReadService.TimelineIndex timeline(@AuthenticationPrincipal AccountPrincipal principal,
                                                    @PathVariable UUID spaceId) {
        return reads.timeline(spaceId, principal.accountId());
    }

    @GetMapping("/timeline/weeks")
    public List<DiaryReadService.WeekSummary> weeks(@AuthenticationPrincipal AccountPrincipal principal,
                                                    @PathVariable UUID spaceId,
                                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return reads.weeks(spaceId, principal.accountId(), month);
    }
}
