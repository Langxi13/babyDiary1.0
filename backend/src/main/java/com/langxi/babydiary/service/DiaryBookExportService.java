package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.CollaborationMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DiaryBookExportService {
    private final SpaceService spaceService;
    private final CollaborationMapper diaryMapper;
    private final AccountSecurityService accountSecurityService;

    @Value("${app.export.pdf-font-path:/usr/share/fonts/truetype/unifont/unifont.ttf}")
    private String fontPath;

    public DiaryBookExportService(SpaceService spaceService,
                                  CollaborationMapper diaryMapper,
                                  AccountSecurityService accountSecurityService) {
        this.spaceService = spaceService;
        this.diaryMapper = diaryMapper;
        this.accountSecurityService = accountSecurityService;
    }

    public BookFile export(String spacePublicId, Integer userId, String format,
                           String startDate, String endDate, String stepUpToken) throws IOException {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        List<Diary> diaries = diaryMapper.findExportDiaries(space.getSpaceId(), userId).stream()
                .filter(diary -> inRange(diary, startDate, endDate)).limit(2000).toList();
        if (diaries.stream().anyMatch(diary -> Boolean.TRUE.equals(diary.getLocked()))) {
            accountSecurityService.requireStepUp(userId, stepUpToken);
        }
        if (diaries.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "所选范围内没有可导出的日记");
        Document document = bookDocument(space.getName(), diaries);
        if ("pdf".equalsIgnoreCase(format)) return pdf(document);
        if ("epub".equalsIgnoreCase(format)) return epub(space.getName(), document);
        throw new BusinessException(ErrorCode.BAD_REQUEST, "导出格式仅支持pdf或epub");
    }

    private BookFile pdf(Document document) throws IOException {
        Path output = Files.createTempFile("baby-diary-book-", ".pdf");
        try (OutputStream stream = Files.newOutputStream(output)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            Path font = Path.of(fontPath);
            if (Files.isRegularFile(font)) builder.useFont(font.toFile(), "Diary CJK");
            builder.withHtmlContent(document.html(), null);
            builder.toStream(stream);
            builder.run();
        } catch (Exception exception) {
            Files.deleteIfExists(output);
            throw exception instanceof IOException io ? io : new IOException("PDF生成失败", exception);
        }
        return new BookFile(new TemporaryFileResource(output), "application/pdf", "Baby-Diary.pdf");
    }

    private BookFile epub(String title, Document document) throws IOException {
        Path output = Files.createTempFile("baby-diary-book-", ".epub");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            ZipEntry mimetype = new ZipEntry("mimetype");
            mimetype.setMethod(ZipEntry.STORED);
            byte[] type = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            mimetype.setSize(type.length);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(type);
            mimetype.setCrc(crc.getValue());
            zip.putNextEntry(mimetype);
            zip.write(type);
            zip.closeEntry();
            writeEntry(zip, "META-INF/container.xml", "<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            writeEntry(zip, "OEBPS/book.xhtml", document.outerHtml());
            writeEntry(zip, "OEBPS/content.opf", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"bookid\" version=\"2.0\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>" + escape(title) + "</dc:title><dc:language>zh-CN</dc:language><dc:identifier id=\"bookid\">baby-diary</dc:identifier></metadata><manifest><item id=\"book\" href=\"book.xhtml\" media-type=\"application/xhtml+xml\"/></manifest><spine><itemref idref=\"book\"/></spine></package>");
        }
        return new BookFile(new TemporaryFileResource(output), "application/epub+zip", "Baby-Diary.epub");
    }

    private Document bookDocument(String title, List<Diary> diaries) {
        Document document = Document.createShell("");
        document.outputSettings().syntax(Document.OutputSettings.Syntax.xml).charset(StandardCharsets.UTF_8);
        document.head().appendElement("meta").attr("charset", "UTF-8");
        document.head().appendElement("style").text("@page{size:A4;margin:22mm}body{font-family:'Diary CJK',sans-serif;color:#2f2b28;line-height:1.75}h1{font-size:28px}article{page-break-inside:avoid;border-top:1px solid #ddd;padding:18px 0}time{color:#777}img{max-width:100%}");
        document.body().appendElement("h1").text(title);
        document.body().appendElement("p").text("导出于 " + LocalDate.now());
        for (Diary diary : diaries) {
            Element article = document.body().appendElement("article");
            article.appendElement("h2").text(diary.getTitle());
            article.appendElement("time").text(diary.getDate().toString());
            Element content = article.appendElement("div").addClass("content");
            if ("html".equals(diary.getContentFormat())) content.html(Jsoup.clean(diary.getContent(), org.jsoup.safety.Safelist.basic()));
            else content.text(diary.getContent());
        }
        return document;
    }

    private boolean inRange(Diary diary, String startDate, String endDate) {
        LocalDate date = diary.getDate().toLocalDate();
        return (startDate == null || date.compareTo(LocalDate.parse(startDate)) >= 0)
                && (endDate == null || date.compareTo(LocalDate.parse(endDate)) <= 0);
    }

    private void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public record BookFile(FileSystemResource resource, String contentType, String filename) {}
}
