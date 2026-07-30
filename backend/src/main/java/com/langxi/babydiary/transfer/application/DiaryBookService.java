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
    private static final DateTimeFormatter CHINESE_DATE =
            DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA);
    private final SpaceAccess spaces;
    private final TransferRepository mapper;
    private final DiaryContentPolicy contentPolicy;
    private final StepUpService stepUp;
    private final String fontPath;

    public DiaryBookService(
            SpaceAccess spaces,
            TransferRepository mapper,
            DiaryContentPolicy contentPolicy,
            StepUpService stepUp,
            @Value("${app.export.pdf-font-path:/usr/share/fonts/truetype/unifont/unifont.ttf}")
                    String fontPath) {
        this.spaces = spaces;
        this.mapper = mapper;
        this.contentPolicy = contentPolicy;
        this.stepUp = stepUp;
        this.fontPath = fontPath;
    }

    public BookFile export(
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
        String spaceName = mapper.findSpaceName(space.internalId());
        Document document = document(spaceName == null ? "Baby Diary" : spaceName, diaries);
        if ("pdf".equalsIgnoreCase(format)) return pdf(document);
        if ("epub".equalsIgnoreCase(format)) return epub(spaceName, document);
        throw ApiException.badRequest("BOOK_FORMAT_INVALID", "导出格式仅支持PDF或EPUB");
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

    private BookFile epub(String title, Document document) throws IOException {
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
            write(zip, "OEBPS/book.xhtml", document.outerHtml());
            write(
                    zip,
                    "OEBPS/content.opf",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"bookid\" version=\"2.0\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>"
                            + escape(title)
                            + "</dc:title><dc:language>zh-CN</dc:language><dc:identifier id=\"bookid\">"
                            + UUID.randomUUID()
                            + "</dc:identifier></metadata><manifest><item id=\"book\" href=\"book.xhtml\" media-type=\"application/xhtml+xml\"/></manifest><spine><itemref idref=\"book\"/></spine></package>");
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(output);
            throw exception;
        }
        return new BookFile(
                new TemporaryDownload(output), "application/epub+zip", "Baby-Diary.epub");
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
