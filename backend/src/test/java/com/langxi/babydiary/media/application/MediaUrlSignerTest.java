package com.langxi.babydiary.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.langxi.babydiary.platform.application.ApiException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class MediaUrlSignerTest {
    private static final String SECRET =
            "media-url-test-secret-that-is-at-least-thirty-two-characters";
    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");
    private static final UUID SPACE_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID ASSET_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID DIARY_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    @Test
    void signsAndVerifiesTheExactVariantProfile() {
        MediaUrlSigner signer = signer();
        MediaAccessContext context = MediaAccessContext.diary(42, DIARY_ID, true);
        URI uri = URI.create(signer.url(SPACE_ID, ASSET_ID, "original", "source", context).url());
        Map<String, String> query = query(uri);

        assertThat(query.get("profile")).isEqualTo("source");
        assertThat(Long.parseLong(query.get("expires")))
                .isGreaterThanOrEqualTo(NOW.plusSeconds(300).getEpochSecond());
        MediaUrlSigner.VerifiedVariant verified =
                signer.verify(
                        SPACE_ID,
                        ASSET_ID,
                        "original",
                        query.get("profile"),
                        query.get("ticket"),
                        Long.parseLong(query.get("expires")),
                        query.get("signature"));
        assertThat(verified.type()).isEqualTo("ORIGINAL");
        assertThat(verified.profile()).isEqualTo("source");
        assertThat(verified.context()).isEqualTo(context);
        assertThat(verified.expiresAt())
                .isEqualTo(Instant.ofEpochSecond(Long.parseLong(query.get("expires"))));

        assertThatThrownBy(
                        () ->
                                signer.verify(
                                        SPACE_ID,
                                        ASSET_ID,
                                        "original",
                                        "default",
                                        query.get("ticket"),
                                        Long.parseLong(query.get("expires")),
                                        query.get("signature")))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("MEDIA_URL_INVALID"));
    }

    @Test
    void rejectsLegacySignaturesWithoutAnAccessContext() throws Exception {
        MediaUrlSigner signer = signer();
        long expires = NOW.plusSeconds(3600).getEpochSecond();
        String payload = SPACE_ID + "\n" + ASSET_ID + "\nORIGINAL\n" + expires;

        assertThatThrownBy(
                        () ->
                                signer.verify(
                                        SPACE_ID,
                                        ASSET_ID,
                                        "original",
                                        "source",
                                        "",
                                        expires,
                                        hmac(payload)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("MEDIA_URL_INVALID"));
    }

    @Test
    void rejectsExpiredAndInvalidProfileUrls() {
        MediaUrlSigner signer = signer();

        assertThatThrownBy(
                        () ->
                                signer.url(
                                        SPACE_ID,
                                        ASSET_ID,
                                        "original",
                                        "../source",
                                        MediaAccessContext.direct(42, false)))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("MEDIA_URL_INVALID"));
        assertThatThrownBy(
                        () ->
                                signer.verify(
                                        SPACE_ID,
                                        ASSET_ID,
                                        "original",
                                        "source",
                                        "ticket",
                                        NOW.minusSeconds(1).getEpochSecond(),
                                        "invalid"))
                .isInstanceOfSatisfying(
                        ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("MEDIA_URL_EXPIRED"));
    }

    private MediaUrlSigner signer() {
        MediaUrlSigner signer =
                new MediaUrlSigner(
                        SECRET,
                        Duration.ofHours(1),
                        Duration.ofMinutes(5),
                        Clock.fixed(NOW, ZoneOffset.UTC));
        signer.initialize();
        return signer;
    }

    private Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getQuery().split("&"))
                .map(value -> value.split("=", 2))
                .collect(Collectors.toMap(value -> value[0], value -> value[1]));
    }

    private String hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
