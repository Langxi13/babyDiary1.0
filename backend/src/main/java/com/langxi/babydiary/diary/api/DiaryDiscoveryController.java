package com.langxi.babydiary.diary.api;

import com.langxi.babydiary.diary.application.DiaryDiscoveryService;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}")
public class DiaryDiscoveryController {
    private final DiaryDiscoveryService discovery;

    public DiaryDiscoveryController(DiaryDiscoveryService discovery) {
        this.discovery = discovery;
    }

    @GetMapping("/search")
    public DiaryDiscoveryService.SearchResponse search(
            @AuthenticationPrincipal AccountPrincipal p,
            @PathVariable UUID spaceId,
            @RequestParam String query,
            @RequestParam(defaultValue = "30") int limit) {
        return discovery.search(spaceId, p.accountId(), query, limit);
    }

    @GetMapping("/insights/yearly")
    public DiaryDiscoveryService.YearInsight yearly(
            @AuthenticationPrincipal AccountPrincipal p,
            @PathVariable UUID spaceId,
            @RequestParam(required = false) Integer year) {
        return discovery.yearly(
                spaceId, p.accountId(), year == null ? LocalDate.now().getYear() : year);
    }
}
