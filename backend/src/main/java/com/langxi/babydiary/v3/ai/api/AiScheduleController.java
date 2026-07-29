package com.langxi.babydiary.v3.ai.api;

import com.langxi.babydiary.v3.ai.application.AiScheduleService;import com.langxi.babydiary.v3.identity.application.V3Principal;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;import java.util.UUID;
@RestController @RequestMapping("/api/v3/spaces/{spaceId}/ai/schedule")
public class AiScheduleController {private final AiScheduleService schedules;public AiScheduleController(AiScheduleService schedules){this.schedules=schedules;}
@GetMapping public AiScheduleService.Schedule get(@AuthenticationPrincipal V3Principal p,@PathVariable UUID spaceId){return schedules.get(spaceId,p.accountId());}
@PutMapping public AiScheduleService.Schedule update(@AuthenticationPrincipal V3Principal p,@PathVariable UUID spaceId,@RequestBody Request r){return schedules.update(spaceId,p.accountId(),r.weeklyEnabled,r.monthlyEnabled,r.annualEnabled);}
public record Request(boolean weeklyEnabled,boolean monthlyEnabled,boolean annualEnabled){}}
