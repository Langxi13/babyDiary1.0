package com.langxi.babydiary.ai.api;

import com.langxi.babydiary.ai.application.AiReportService;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}/ai-reports")
public class AiReportController {
    private final AiReportService reports;

    public AiReportController(AiReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public ReportPageResponse list(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        AiReportService.ReportPage result =
                reports.list(spaceId, principal.accountId(), type, page, size);
        return new ReportPageResponse(
                result.content().stream().map(ReportSummary::from).toList(),
                result.pageNumber(),
                result.pageSize(),
                result.totalElements(),
                result.totalPages());
    }

    @GetMapping("/{reportId}")
    public ReportResponse detail(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID reportId) {
        return ReportResponse.from(reports.detail(spaceId, reportId, principal.accountId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse generate(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @Valid @RequestBody GenerateRequest request) {
        return ReportResponse.from(
                reports.generate(spaceId, principal.accountId(), request.type(), request.period()));
    }

    @DeleteMapping("/{reportId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @PathVariable UUID reportId) {
        reports.delete(spaceId, reportId, principal.accountId());
    }

    public record GenerateRequest(
            @NotBlank @Pattern(regexp = "WEEKLY|MONTHLY|ANNUAL") String type,
            @NotBlank String period) {}

    public record ReportSummary(
            UUID id,
            UUID spaceId,
            String periodType,
            LocalDate start,
            LocalDate end,
            String title,
            int diaryCount,
            String model,
            LocalDateTime createdAt) {
        static ReportSummary from(AiReportService.ReportView report) {
            return new ReportSummary(
                    report.id(),
                    report.spaceId(),
                    report.periodType(),
                    report.start(),
                    report.end(),
                    report.title(),
                    report.diaryCount(),
                    report.model(),
                    report.createdAt());
        }
    }

    public record ReportPageResponse(
            java.util.List<ReportSummary> content,
            int pageNumber,
            int pageSize,
            long totalElements,
            long totalPages) {}

    public record ReportResponse(
            UUID id,
            UUID spaceId,
            String periodType,
            LocalDate start,
            LocalDate end,
            String title,
            String contentMarkdown,
            int diaryCount,
            String model,
            LocalDateTime createdAt) {
        static ReportResponse from(AiReportService.ReportView report) {
            return new ReportResponse(
                    report.id(),
                    report.spaceId(),
                    report.periodType(),
                    report.start(),
                    report.end(),
                    report.title(),
                    report.markdown(),
                    report.diaryCount(),
                    report.model(),
                    report.createdAt());
        }
    }
}
