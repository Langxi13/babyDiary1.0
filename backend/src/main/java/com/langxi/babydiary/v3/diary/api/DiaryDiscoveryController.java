package com.langxi.babydiary.v3.diary.api;

import com.langxi.babydiary.v3.diary.application.DiaryDiscoveryService;
import com.langxi.babydiary.v3.identity.application.V3Principal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController @RequestMapping("/api/v3/spaces/{spaceId}")
public class DiaryDiscoveryController {
    private final DiaryDiscoveryService discovery; public DiaryDiscoveryController(DiaryDiscoveryService discovery){this.discovery=discovery;}
    @GetMapping("/search") public DiaryDiscoveryService.SearchResponse search(@AuthenticationPrincipal V3Principal p,@PathVariable UUID spaceId,@RequestParam String query,@RequestParam(defaultValue="30") int limit){return discovery.search(spaceId,p.accountId(),query,limit);}
    @GetMapping("/insights/yearly") public DiaryDiscoveryService.YearInsight yearly(@AuthenticationPrincipal V3Principal p,@PathVariable UUID spaceId,@RequestParam(required=false) Integer year){return discovery.yearly(spaceId,p.accountId(),year==null?LocalDate.now().getYear():year);}
}
