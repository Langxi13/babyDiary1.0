package com.langxi.babydiary.ai.api;

import com.langxi.babydiary.ai.application.AiScheduleService;import com.langxi.babydiary.identity.application.AccountPrincipal;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;import java.util.UUID;
@RestController @RequestMapping("/api/v3/spaces/{spaceId}/ai/schedule")
public class AiScheduleController {private final AiScheduleService schedules;public AiScheduleController(AiScheduleService schedules){this.schedules=schedules;}
@GetMapping public AiScheduleService.Schedule get(@AuthenticationPrincipal AccountPrincipal p,@PathVariable UUID spaceId){return schedules.get(spaceId,p.accountId());}
@PutMapping public AiScheduleService.Schedule update(@AuthenticationPrincipal AccountPrincipal p,@PathVariable UUID spaceId,@RequestBody Request r){return schedules.update(spaceId,p.accountId(),r.weeklyEnabled,r.monthlyEnabled,r.annualEnabled);}
public record Request(boolean weeklyEnabled,boolean monthlyEnabled,boolean annualEnabled){}}
