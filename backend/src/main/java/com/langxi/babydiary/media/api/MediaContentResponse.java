package com.langxi.babydiary.media.api;

import com.langxi.babydiary.storage.StoredObject;
import com.langxi.babydiary.media.application.MediaService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.Duration;

final class MediaContentResponse {
    private MediaContentResponse() {
    }

    static ResponseEntity<StreamingResponseBody> create(MediaService media,
                                                         MediaService.ResolvedVariant resolved,
                                                         String rangeHeader, String ifNoneMatch,
                                                         boolean head, boolean noStore) {
        long total = resolved.variant().sizeBytes();
        if ((rangeHeader == null || rangeHeader.isBlank()) && matches(ifNoneMatch, resolved.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(resolved.etag())
                    .cacheControl(noStore ? CacheControl.noStore()
                            : CacheControl.maxAge(Duration.ofMinutes(15)).cachePrivate())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes").build();
        }
        ByteRange range = range(rangeHeader, total);
        HttpStatus status = range.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType(resolved.variant().contentType()));
        headers.setContentLength(range.length());
        headers.setETag(resolved.etag());
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
        headers.set("X-Content-Type-Options", "nosniff");
        if (range.partial()) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + range.start() + '-' + range.end() + '/' + total);
        }
        headers.setCacheControl(noStore ? CacheControl.noStore()
                : CacheControl.maxAge(Duration.ofMinutes(15)).cachePrivate());
        if (head) return new ResponseEntity<>(null, headers, status);
        StreamingResponseBody body = output -> {
            try (StoredObject object = media.open(resolved, range.start(), range.length())) {
                object.stream().transferTo(output);
            }
        };
        return new ResponseEntity<>(body, headers, status);
    }

    private static ByteRange range(String header, long total) {
        if (total < 0) throw new IllegalArgumentException("Negative media length");
        if (header == null || header.isBlank()) return new ByteRange(0, total - 1, false);
        if (!header.startsWith("bytes=") || header.indexOf(',') >= 0 || total == 0) throw invalidRange(total);
        String value = header.substring(6).trim();
        int separator = value.indexOf('-');
        if (separator < 0) throw invalidRange(total);
        try {
            String startValue = value.substring(0, separator).trim();
            String endValue = value.substring(separator + 1).trim();
            long start;
            long end;
            if (startValue.isEmpty()) {
                long suffix = Long.parseLong(endValue);
                if (suffix <= 0) throw invalidRange(total);
                start = Math.max(0, total - suffix);
                end = total - 1;
            } else {
                start = Long.parseLong(startValue);
                end = endValue.isEmpty() ? total - 1 : Long.parseLong(endValue);
            }
            if (start < 0 || end < start || start >= total) throw invalidRange(total);
            end = Math.min(end, total - 1);
            return new ByteRange(start, end, true);
        } catch (NumberFormatException exception) {
            throw invalidRange(total);
        }
    }

    private static MediaRangeException invalidRange(long total) {
        return new MediaRangeException(total);
    }

    private static boolean matches(String value, String etag) {
        if (value == null || value.isBlank()) return false;
        for (String candidate : value.split(",")) {
            if ("*".equals(candidate.trim()) || etag.equals(candidate.trim())) return true;
        }
        return false;
    }

    private static MediaType contentType(String value) {
        try { return value == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(value); }
        catch (RuntimeException ignored) { return MediaType.APPLICATION_OCTET_STREAM; }
    }

    record ByteRange(long start, long end, boolean partial) {
        long length() { return end < start ? 0 : end - start + 1; }
    }

}
