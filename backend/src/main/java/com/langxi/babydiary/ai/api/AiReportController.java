package com.langxi.babydiary.ai.api;

import com.langxi.babydiary.ai.application.AiReportRepository;
import com.langxi.babydiary.ai.application.AiReportService;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public List<ReportSummary> list(
            @AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID spaceId) {
        return reports.list(spaceId, principal.accountId()).stream()
                .map(ReportSummary::from)
                .toList();
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
            @NotBlank @Pattern(regexp = "WEEKLY|MONTHLY") String type, @NotBlank String period) {}

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
        static ReportSummary from(AiReportRepository.Report report) {
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
        static ReportResponse from(AiReportRepository.Report report) {
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
