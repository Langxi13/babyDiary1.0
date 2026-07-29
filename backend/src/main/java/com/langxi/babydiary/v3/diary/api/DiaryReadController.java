package com.langxi.babydiary.v3.diary.api;

import com.langxi.babydiary.v3.diary.application.DiaryReadService;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/diaries")
public class DiaryReadController {
    private final DiaryReadService reads;

    public DiaryReadController(DiaryReadService reads) {
        this.reads = reads;
    }

    @GetMapping("/calendar")
    public DiaryReadService.CalendarMonth calendar(@AuthenticationPrincipal V3Principal principal,
                                                    @PathVariable UUID spaceId,
                                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return reads.calendar(spaceId, principal.accountId(), month);
    }

    @GetMapping("/timeline")
    public DiaryReadService.TimelineIndex timeline(@AuthenticationPrincipal V3Principal principal,
                                                    @PathVariable UUID spaceId) {
        return reads.timeline(spaceId, principal.accountId());
    }

    @GetMapping("/timeline/weeks")
    public List<DiaryReadService.WeekSummary> weeks(@AuthenticationPrincipal V3Principal principal,
                                                    @PathVariable UUID spaceId,
                                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return reads.weeks(spaceId, principal.accountId(), month);
    }
}
