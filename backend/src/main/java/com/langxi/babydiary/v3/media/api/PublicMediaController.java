package com.langxi.babydiary.v3.media.api;

import com.langxi.babydiary.storage.StoredObject;
import com.langxi.babydiary.v3.media.application.MediaService;
import com.langxi.babydiary.v3.media.application.MediaUrlSigner;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v3/public/media")
public class PublicMediaController {
    private final MediaService media;
    private final MediaUrlSigner signer;

    public PublicMediaController(MediaService media, MediaUrlSigner signer) {
        this.media = media;
        this.signer = signer;
    }

    @GetMapping("/{spaceId}/{assetId}/{variant}")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID spaceId, @PathVariable UUID assetId,
                                                          @PathVariable String variant, @RequestParam long expires,
                                                          @RequestParam String signature) throws IOException {
        String verifiedVariant = signer.verify(spaceId, assetId, variant, expires, signature);
        StoredObject object = media.openSignedVariant(spaceId, assetId, verifiedVariant);
        String contentType = object.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : object.contentType();
        StreamingResponseBody body = output -> {
            try (object) {
                object.stream().transferTo(output);
            }
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).contentLength(object.length())
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(55)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline").body(body);
    }
}
