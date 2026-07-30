package com.langxi.babydiary.media.application;

import java.util.Locale;
import java.util.UUID;

public record MediaAccessContext(Source source, long accountId, UUID contextId, boolean elevated) {
  public static MediaAccessContext direct(long accountId, boolean elevated) {
    return new MediaAccessContext(Source.DIRECT, accountId, null, elevated);
  }

  public static MediaAccessContext diary(long accountId, UUID diaryId, boolean elevated) {
    return new MediaAccessContext(Source.DIARY, accountId, diaryId, elevated);
  }

  public static MediaAccessContext album(long accountId, UUID albumId, boolean elevated) {
    return new MediaAccessContext(Source.ALBUM, accountId, albumId, elevated);
  }

  public static MediaAccessContext anniversary(
      long accountId, UUID anniversaryId, boolean elevated) {
    return new MediaAccessContext(Source.ANNIVERSARY, accountId, anniversaryId, elevated);
  }

  public static MediaAccessContext avatar(long accountId, UUID accountPublicId, boolean elevated) {
    return new MediaAccessContext(Source.AVATAR, accountId, accountPublicId, elevated);
  }

  public static MediaAccessContext share(UUID shareId) {
    return new MediaAccessContext(Source.SHARE, 0, shareId, false);
  }

  String serialize() {
    return source.name()
        + ":"
        + accountId
        + ":"
        + (contextId == null ? "" : contextId)
        + ":"
        + (elevated ? "1" : "0");
  }

  static MediaAccessContext parse(String value) {
    try {
      String[] fields = value.split(":", -1);
      if (fields.length != 4) throw new IllegalArgumentException();
      Source source = Source.valueOf(fields[0].toUpperCase(Locale.ROOT));
      long accountId = Long.parseLong(fields[1]);
      UUID contextId = fields[2].isBlank() ? null : UUID.fromString(fields[2]);
      boolean elevated = "1".equals(fields[3]);
      if (source != Source.DIRECT && contextId == null) throw new IllegalArgumentException();
      if (source == Source.SHARE && accountId != 0) throw new IllegalArgumentException();
      if (source != Source.SHARE && accountId <= 0) throw new IllegalArgumentException();
      return new MediaAccessContext(source, accountId, contextId, elevated);
    } catch (RuntimeException exception) {
      throw com.langxi.babydiary.platform.application.ApiException.notFound(
          "MEDIA_URL_INVALID", "媒体访问地址无效");
    }
  }

  public enum Source {
    DIRECT,
    DIARY,
    ALBUM,
    ANNIVERSARY,
    AVATAR,
    SHARE
  }
}
