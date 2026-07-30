package com.langxi.babydiary.v3.media.application;

import com.langxi.babydiary.v3.media.domain.MediaAsset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MediaVariantPolicy {
  public Optional<MediaAsset.Variant> select(
      List<MediaAsset.Variant> variants, String type, String profile) {
    String normalizedType = normalizeType(type);
    String normalizedProfile = normalizeProfile(profile);
    return variants.stream()
        .filter(value -> "READY".equals(value.status()) && normalizedType.equals(value.type()))
        .filter(value -> normalizedProfile == null || normalizedProfile.equals(value.profile()))
        .min(
            Comparator.<MediaAsset.Variant>comparingInt(
                    value -> rank(normalizedType, value.profile()))
                .thenComparing(MediaAsset.Variant::profile));
  }

  public String normalizeType(String value) {
    String type =
        value == null || value.isBlank() ? "ORIGINAL" : value.trim().toUpperCase(Locale.ROOT);
    if (!List.of("ORIGINAL", "THUMBNAIL", "POSTER", "WAVEFORM", "TRANSCODED").contains(type)) {
      throw com.langxi.babydiary.v3.platform.application.V3Exception.notFound(
          "MEDIA_VARIANT_NOT_FOUND", "媒体变体不存在");
    }
    return type;
  }

  public String normalizeProfile(String value) {
    if (value == null || value.isBlank()) return null;
    String profile = value.trim().toLowerCase(Locale.ROOT);
    if (!profile.matches("[a-z0-9][a-z0-9._-]{0,31}")) {
      throw com.langxi.babydiary.v3.platform.application.V3Exception.notFound(
          "MEDIA_VARIANT_NOT_FOUND", "媒体变体不存在");
    }
    return profile;
  }

  private int rank(String type, String profile) {
    if ("ORIGINAL".equals(type)) {
      if ("source".equals(profile)) return 0;
      if ("default".equals(profile)) return 1;
    } else {
      if ("default".equals(profile)) return 0;
      if ("source".equals(profile)) return 1;
    }
    return 2;
  }
}
