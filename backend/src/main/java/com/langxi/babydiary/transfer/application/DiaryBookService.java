package com.langxi.babydiary.transfer.application;

import com.langxi.babydiary.diary.application.DiaryContentPolicy;
import com.langxi.babydiary.identity.application.AccountPrincipal;
import com.langxi.babydiary.identity.application.StepUpService;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.space.application.SpaceAccess;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DiaryBookService {
    private static final int MAX_DIARIES = 2_000;
    private static final Semaphore EXPORT_SLOT = new Semaphore(1, true);
    private static final DateTimeFormatter CHINESE_DATE =
            DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA);
    private final SpaceAccess spaces;
    private final TransferRepository mapper;
    private final DiaryContentPolicy contentPolicy;
    private final StepUpService stepUp;
    private final String fontPath;
    private final long renderMaxBytes;

    public DiaryBookService(
            SpaceAccess spaces,
            TransferRepository mapper,
            DiaryContentPolicy contentPolicy,
            StepUpService stepUp,
            @Value("${app.export.pdf-font-path:/usr/share/fonts/truetype/unifont/unifont.ttf}")
                    String fontPath,
            @Value("${app.export.render-max-bytes:8388608}") long renderMaxBytes) {
        this.spaces = spaces;
        this.mapper = mapper;
        this.contentPolicy = contentPolicy;
        this.stepUp = stepUp;
        this.fontPath = fontPath;
        this.renderMaxBytes = Math.max(1, renderMaxBytes);
    }

    public BookFile export(
            UUID spaceId,
            AccountPrincipal principal,
            String format,
            LocalDate startDate,
            LocalDate endDate,
            String stepUpToken)
            throws IOException {
        String normalizedFormat = format == null ? "" : format.toLowerCase(Locale.ROOT);
        if (!"pdf".equals(normalizedFormat) && !"epub".equals(normalizedFormat)) {
            throw ApiException.badRequest("BOOK_FORMAT_INVALID", "导出格式仅支持PDF或EPUB");
        }
        if (!EXPORT_SLOT.tryAcquire()) {
            throw ApiException.tooManyRequests("EXPORT_BUSY", "已有日记书正在生成，请稍后重试");
        }
        try {
            return exportExclusive(
                    spaceId, principal, normalizedFormat, startDate, endDate, stepUpToken);
        } finally {
            EXPORT_SLOT.release();
        }
    }

    private BookFile exportExclusive(
            UUID spaceId,
            AccountPrincipal principal,
            String format,
            LocalDate startDate,
            LocalDate endDate,
            String stepUpToken)
            throws IOException {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw ApiException.badRequest("DATE_RANGE_INVALID", "结束日期不能早于开始日期");
        }
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, principal.accountId());
        List<TransferRepository.DiaryData> diaries =
                mapper.findDiaries(
                        space.internalId(),
                        principal.accountId(),
                        startDate,
                        endDate,
                        MAX_DIARIES + 1);
        if (diaries.isEmpty()) throw ApiException.notFound("BOOK_EMPTY", "所选范围内没有可导出的日记");
        if (diaries.size() > MAX_DIARIES) {
            throw ApiException.badRequest("BOOK_TOO_MANY_DIARIES", "单次最多导出2000篇日记");
        }
        if (diaries.stream().anyMatch(TransferRepository.DiaryData::locked))
            stepUp.require(principal, stepUpToken);
        long renderBytes = 0;
        for (TransferRepository.DiaryData diary : diaries) {
            renderBytes += utf8Length(diary.title()) + utf8Length(diary.contentHtml());
            if (renderBytes > renderMaxBytes) {
                throw ApiException.payloadTooLarge("EXPORT_TOO_LARGE", "日记书内容超过服务器单次渲染上限");
            }
        }
        String spaceName = mapper.findSpaceName(space.internalId());
        String title = spaceName == null ? "Baby Diary" : spaceName;
        if ("pdf".equals(format)) return pdf(document(title, diaries));
        return epub(title, diaries);
    }

    private int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private Document document(String title, List<TransferRepository.DiaryData> diaries) {
        Document document = Document.createShell("");
        document.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .charset(StandardCharsets.UTF_8);
        document.head().appendElement("meta").attr("charset", "UTF-8");
        document.head().appendElement("title").text(title);
        document.head()
                .appendElement("style")
                .text(
                        """
                @page{size:A4;margin:20mm}body{font-family:'Diary CJK',sans-serif;color:#302b28;line-height:1.75}
                h1{font-size:28px;margin:0 0 8px}.export-date{color:#786f69;margin:0 0 28px}
                article{page-break-inside:avoid;border-top:1px solid #ddd5d0;padding:18px 0}
                h2{font-size:20px;margin:0 0 4px}time{color:#786f69;font-size:13px}.content{margin-top:12px}
                blockquote{border-left:3px solid #b58c91;padding-left:12px;color:#615955}pre{white-space:pre-wrap}
                """);
        document.body().appendElement("h1").text(title);
        document.body()
                .appendElement("p")
                .addClass("export-date")
                .text("导出日期：" + LocalDate.now().format(CHINESE_DATE));
        for (TransferRepository.DiaryData diary : diaries) {
            Element article = document.body().appendElement("article");
            article.appendElement("h2").text(diary.title());
            article.appendElement("time").text(diary.diaryDate().format(CHINESE_DATE));
            article.appendElement("div")
                    .addClass("content")
                    .html(contentPolicy.normalize(diary.contentHtml()).html());
        }
        return document;
    }

    private BookFile pdf(Document document) throws IOException {
        Path output = Files.createTempFile("baby-diary-v3-book-", ".pdf");
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
        return new BookFile(new TemporaryDownload(output), "application/pdf", "Baby-Diary.pdf");
    }

    private BookFile epub(String title, List<TransferRepository.DiaryData> diaries)
            throws IOException {
        Path output = Files.createTempFile("baby-diary-v3-book-", ".epub");
        try (ZipOutputStream zip =
                new ZipOutputStream(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
            byte[] type = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry mimetype = new ZipEntry("mimetype");
            CRC32 crc = new CRC32();
            crc.update(type);
            mimetype.setMethod(ZipEntry.STORED);
            mimetype.setSize(type.length);
            mimetype.setCompressedSize(type.length);
            mimetype.setCrc(crc.getValue());
            zip.putNextEntry(mimetype);
            zip.write(type);
            zip.closeEntry();
            write(
                    zip,
                    "META-INF/container.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            StringBuilder manifest = new StringBuilder();
            StringBuilder spine = new StringBuilder();
            StringBuilder navigation = new StringBuilder();
            for (int index = 0; index < diaries.size(); index++) {
                String id = "chapter-" + (index + 1);
                String href = id + ".xhtml";
                write(zip, "OEBPS/" + href, chapter(title, diaries.get(index)).outerHtml());
                manifest.append("<item id=\"")
                        .append(id)
                        .append("\" href=\"")
                        .append(href)
                        .append("\" media-type=\"application/xhtml+xml\"/>");
                spine.append("<itemref idref=\"").append(id).append("\"/>");
                navigation
                        .append("<li><a href=\"")
                        .append(href)
                        .append("\">")
                        .append(escape(diaries.get(index).title()))
                        .append("</a></li>");
            }
            write(
                    zip,
                    "OEBPS/book.xhtml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>"
                            + escape(title)
                            + "</title></head><body><h1>"
                            + escape(title)
                            + "</h1><ol>"
                            + navigation
                            + "</ol></body></html>");
            write(
                    zip,
                    "OEBPS/content.opf",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"bookid\" version=\"2.0\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>"
                            + escape(title)
                            + "</dc:title><dc:language>zh-CN</dc:language><dc:identifier id=\"bookid\">"
                            + UUID.randomUUID()
                            + "</dc:identifier></metadata><manifest><item id=\"book\" href=\"book.xhtml\" media-type=\"application/xhtml+xml\"/>"
                            + manifest
                            + "</manifest><spine><itemref idref=\"book\"/>"
                            + spine
                            + "</spine></package>");
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(output);
            throw exception;
        }
        return new BookFile(
                new TemporaryDownload(output), "application/epub+zip", "Baby-Diary.epub");
    }

    private Document chapter(String bookTitle, TransferRepository.DiaryData diary) {
        Document document = Document.createShell("");
        document.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .charset(StandardCharsets.UTF_8);
        document.head().appendElement("meta").attr("charset", "UTF-8");
        document.head().appendElement("title").text(diary.title() + " - " + bookTitle);
        document.head()
                .appendElement("style")
                .text(
                        "body{font-family:sans-serif;line-height:1.75;color:#302b28}h1{font-size:1.4em}time{color:#786f69}.content{margin-top:1em}pre{white-space:pre-wrap}");
        document.body().appendElement("h1").text(diary.title());
        document.body().appendElement("time").text(diary.diaryDate().format(CHINESE_DATE));
        document.body()
                .appendElement("div")
                .addClass("content")
                .html(contentPolicy.normalize(diary.contentHtml()).html());
        return document;
    }

    private void write(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String escape(String value) {
        return (value == null ? "Baby Diary" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public record BookFile(TemporaryDownload resource, String contentType, String filename) {}
}
