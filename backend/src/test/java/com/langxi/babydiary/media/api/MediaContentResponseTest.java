package com.langxi.babydiary.media.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.langxi.babydiary.media.application.MediaService;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.storage.StoredObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class MediaContentResponseTest {
    private static final byte[] CONTENT = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    @Test
    void streamsSingleByteRangeWithProtocolHeaders() throws Exception {
        MediaService media = mock(MediaService.class);
        MediaService.ResolvedVariant resolved = resolved();
        when(media.open(resolved, 2, 5)).thenReturn(object(2, 5));

        var response =
                MediaContentResponse.create(media, resolved, "bytes=2-6", null, false, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 2-6/16");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(5);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(response.getHeaders().getCacheControl()).contains("public");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var input = response.getBody().getInputStream()) {
            input.transferTo(output);
        }
        assertThat(output.toByteArray())
                .containsExactly("23456".getBytes(StandardCharsets.US_ASCII));
        verify(media).open(resolved, 2, 5);
    }

    @Test
    void supportsSuffixRangesAndHeadWithoutOpeningStorage() throws Exception {
        MediaService media = mock(MediaService.class);
        MediaService.ResolvedVariant resolved = resolved();

        var response = MediaContentResponse.create(media, resolved, "bytes=-4", null, true, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes 12-15/16");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(4);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void returnsProtectedNotModifiedResponsesWithoutCaching() throws Exception {
        var response =
                MediaContentResponse.create(
                        mock(MediaService.class), resolved(), null, "\"checksum\"", false, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getETag()).isEqualTo("\"checksum\"");
    }

    @Test
    void rejectsMultipleAndUnsatisfiableRanges() {
        assertThatThrownBy(
                        () ->
                                MediaContentResponse.create(
                                        mock(MediaService.class),
                                        resolved(),
                                        "bytes=0-1,4-5",
                                        null,
                                        false,
                                        false))
                .isInstanceOf(MediaRangeException.class)
                .extracting(exception -> ((MediaRangeException) exception).total())
                .isEqualTo(16L);
        assertThatThrownBy(
                        () ->
                                MediaContentResponse.create(
                                        mock(MediaService.class),
                                        resolved(),
                                        "bytes=16-20",
                                        null,
                                        false,
                                        false))
                .isInstanceOf(MediaRangeException.class);
    }

    private MediaService.ResolvedVariant resolved() {
        MediaAsset.Variant variant =
                new MediaAsset.Variant(
                        "ORIGINAL",
                        "source",
                        "LOCAL",
                        "object",
                        "image/png",
                        CONTENT.length,
                        null,
                        2,
                        2,
                        null,
                        null,
                        "READY");
        return new MediaService.ResolvedVariant(null, variant, "\"checksum\"");
    }

    private StoredObject object(int offset, int length) {
        return new StoredObject(
                new ByteArrayInputStream(CONTENT, offset, length), length, "image/png");
    }
}
