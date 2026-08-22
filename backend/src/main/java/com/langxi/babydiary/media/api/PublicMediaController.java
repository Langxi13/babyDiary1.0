package com.langxi.babydiary.media.api;

import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.application.MediaService;
import com.langxi.babydiary.media.application.MediaUrlSigner;
import com.langxi.babydiary.platform.api.ApiContract;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping((ApiContract.ROOT + "/public/media"))
public class PublicMediaController {
    private final MediaService media;
    private final MediaUrlSigner signer;

    public PublicMediaController(MediaService media, MediaUrlSigner signer) {
        this.media = media;
        this.signer = signer;
    }

    @RequestMapping(
            value = "/{spaceId}/{assetId}/{variant}",
            method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<StreamingResponseBody> content(
            @PathVariable UUID spaceId,
            @PathVariable UUID assetId,
            @PathVariable String variant,
            @RequestParam String profile,
            @RequestParam String ticket,
            @RequestParam long expires,
            @RequestParam String signature,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            HttpServletRequest request) {
        MediaUrlSigner.VerifiedVariant verified =
                signer.verify(spaceId, assetId, variant, profile, ticket, expires, signature);
        MediaService.ResolvedVariant resolved =
                media.resolveSignedVariant(
                        spaceId, assetId, verified.type(), verified.profile(), verified.context());
        MediaAccessContext context = verified.context();
        boolean noStore = context.elevated() || context.source() == MediaAccessContext.Source.SHARE;
        return MediaContentResponse.create(
                media,
                resolved,
                range,
                ifNoneMatch,
                "HEAD".equals(request.getMethod()),
                noStore,
                Duration.between(Instant.now(), verified.expiresAt()));
    }
}
