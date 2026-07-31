package com.langxi.babydiary.transfer.api;

import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.transfer.application.DiaryBookService;
import com.langxi.babydiary.transfer.application.DiaryMediaExportService;
import com.langxi.babydiary.transfer.application.PortableArchiveService;
import com.langxi.babydiary.transfer.application.TemporaryDownload;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v3/spaces/{spaceId}")
public class TransferController {
    private final PortableArchiveService archives;
    private final DiaryBookService books;
    private final DiaryMediaExportService mediaExports;

    public TransferController(
            PortableArchiveService archives,
            DiaryBookService books,
            DiaryMediaExportService mediaExports) {
        this.archives = archives;
        this.books = books;
        this.mediaExports = mediaExports;
    }

    @GetMapping("/transfer/export")
    public ResponseEntity<TemporaryDownload> exportArchive(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken)
            throws IOException {
        TemporaryDownload file = archives.exportSpace(spaceId, principal, stepUpToken);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=Baby-Diary-export.zip")
                .body(file);
    }

    @PostMapping(value = "/transfer/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PortableArchiveService.ImportResult importArchive(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestParam("archive") MultipartFile archive,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken)
            throws IOException {
        return archives.importSpace(spaceId, principal, archive, stepUpToken);
    }

    @GetMapping("/books")
    public ResponseEntity<TemporaryDownload> exportBook(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken)
            throws IOException {
        DiaryBookService.BookFile book =
                books.export(spaceId, principal, format, startDate, endDate, stepUpToken);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(book.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + book.filename())
                .body(book.resource());
    }

    @GetMapping("/transfer/media")
    public ResponseEntity<TemporaryDownload> exportMedia(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID spaceId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestHeader(value = "X-Step-Up-Token", required = false) String stepUpToken)
            throws IOException {
        TemporaryDownload file =
                mediaExports.export(spaceId, principal, startDate, endDate, stepUpToken);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=Baby-Diary-images-"
                                + startDate
                                + "-"
                                + endDate
                                + ".zip")
                .body(file);
    }
}
