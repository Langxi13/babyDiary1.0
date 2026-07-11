package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.DiaryBookExportService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v2/spaces/{spaceId}/books")
public class DiaryBookController {
    private final DiaryBookExportService exportService;
    private final CurrentUser currentUser;

    public DiaryBookController(DiaryBookExportService exportService, CurrentUser currentUser) {
        this.exportService = exportService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<org.springframework.core.io.FileSystemResource> export(
            @PathVariable String spaceId,
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken) throws IOException {
        DiaryBookExportService.BookFile book = exportService.export(
                spaceId, currentUser.getUserId(), format, startDate, endDate, stepUpToken);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(book.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + book.filename())
                .body(book.resource());
    }
}
