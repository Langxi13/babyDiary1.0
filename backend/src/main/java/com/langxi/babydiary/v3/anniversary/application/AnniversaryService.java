package com.langxi.babydiary.v3.anniversary.application;

import com.langxi.babydiary.v3.anniversary.domain.Anniversary;
import com.langxi.babydiary.v3.media.application.MediaRepository;
import com.langxi.babydiary.v3.media.domain.MediaAsset;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.space.application.SpaceAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AnniversaryService {
    private final SpaceAccess spaces;
    private final AnniversaryRepository anniversaries;
    private final MediaRepository media;

    public AnniversaryService(SpaceAccess spaces, AnniversaryRepository anniversaries, MediaRepository media) {
        this.spaces = spaces;
        this.anniversaries = anniversaries;
        this.media = media;
    }

    public List<Anniversary> list(UUID spaceId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return anniversaries.findForSpace(space.internalId());
    }

    @Transactional
    public Anniversary create(UUID spaceId, long accountId, Command command) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        Validated value = validate(space.internalId(), accountId, command);
        UUID publicId = UUID.randomUUID();
        anniversaries.insert(new AnniversaryRepository.NewAnniversary(publicId, space.internalId(), accountId,
                value.title(), value.date(), value.description(), value.coverAssetId(), value.sortOrder()));
        return require(space.internalId(), publicId);
    }

    @Transactional
    public Anniversary update(UUID spaceId, UUID anniversaryId, long accountId, Command command) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        require(space.internalId(), anniversaryId);
        Validated value = validate(space.internalId(), accountId, command);
        if (!anniversaries.update(space.internalId(), anniversaryId, new AnniversaryRepository.UpdatedAnniversary(
                value.title(), value.date(), value.description(), value.coverAssetId(), value.sortOrder()))) {
            throw V3Exception.notFound("ANNIVERSARY_NOT_FOUND", "纪念日不存在或无权访问");
        }
        return require(space.internalId(), anniversaryId);
    }

    @Transactional
    public void delete(UUID spaceId, UUID anniversaryId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (!anniversaries.softDelete(space.internalId(), anniversaryId, LocalDateTime.now(ZoneOffset.UTC))) {
            throw V3Exception.notFound("ANNIVERSARY_NOT_FOUND", "纪念日不存在或无权访问");
        }
    }

    private Validated validate(long spaceId, long accountId, Command command) {
        String title = command.title() == null ? "" : command.title().trim();
        if (title.isBlank()) throw V3Exception.badRequest("ANNIVERSARY_TITLE_REQUIRED", "纪念日名称不能为空");
        if (title.length() > 100) throw V3Exception.badRequest("ANNIVERSARY_TITLE_TOO_LONG", "纪念日名称不能超过100个字符");
        if (command.date() == null) throw V3Exception.badRequest("ANNIVERSARY_DATE_REQUIRED", "请选择纪念日日期");
        String description = command.description() == null || command.description().isBlank()
                ? null : command.description().trim();
        Long coverId = null;
        if (command.coverAssetId() != null) {
            coverId = media.findByPublicIds(spaceId, List.of(command.coverAssetId()), accountId).stream()
                    .filter(asset -> "IMAGE".equals(asset.mediaType()))
                    .map(MediaAsset::internalId).findFirst()
                    .orElseThrow(() -> V3Exception.badRequest("COVER_MEDIA_INVALID", "封面图片不存在或不属于当前空间"));
        }
        return new Validated(title, command.date(), description, coverId, Math.max(0, command.sortOrder()));
    }

    private Anniversary require(long spaceId, UUID publicId) {
        return anniversaries.findByPublicId(spaceId, publicId)
                .orElseThrow(() -> V3Exception.notFound("ANNIVERSARY_NOT_FOUND", "纪念日不存在或无权访问"));
    }

    public record Command(String title, LocalDate date, String description, UUID coverAssetId, int sortOrder) {
    }

    private record Validated(String title, LocalDate date, String description, Long coverAssetId, int sortOrder) {
    }
}
