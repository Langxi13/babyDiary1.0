package com.langxi.babydiary.anniversary.application;

import com.langxi.babydiary.anniversary.domain.Anniversary;
import com.langxi.babydiary.media.application.MediaRepository;
import com.langxi.babydiary.media.application.MediaAccessPolicy;
import com.langxi.babydiary.media.application.MediaAccessContext;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.space.application.SpaceAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnniversaryService {
    private final SpaceAccess spaces;
    private final AnniversaryRepository anniversaries;
    private final MediaRepository media;
    private final MediaAccessPolicy mediaAccess;

    public AnniversaryService(SpaceAccess spaces, AnniversaryRepository anniversaries, MediaRepository media,
                              MediaAccessPolicy mediaAccess) {
        this.spaces = spaces;
        this.anniversaries = anniversaries;
        this.media = media;
        this.mediaAccess=mediaAccess;
    }

    public List<Item> list(UUID spaceId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        List<Anniversary> rows=anniversaries.findForSpace(space.internalId());
        Map<UUID,MediaAsset> covers=media.findByPublicIdsInSpace(space.internalId(),rows.stream()
                .map(Anniversary::coverAssetId).filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(MediaAsset::id,value->value));
        return rows.stream().map(row->new Item(row,covers.get(row.coverAssetId()))).toList();
    }

    @Transactional
    public Item create(UUID spaceId, long accountId, Command command,boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        Validated value = validate(spaceId,space.internalId(), accountId, command,elevated);
        UUID publicId = UUID.randomUUID();
        anniversaries.insert(new AnniversaryRepository.NewAnniversary(publicId, space.internalId(), accountId,
                value.title(), value.date(), value.description(), value.coverAssetId(), value.sortOrder()));
        return item(space.internalId(),require(space.internalId(), publicId));
    }

    @Transactional
    public Item update(UUID spaceId, UUID anniversaryId, long accountId, Command command,boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        require(space.internalId(), anniversaryId);
        Validated value = validate(spaceId,space.internalId(), accountId, command,elevated);
        if (!anniversaries.update(space.internalId(), anniversaryId, new AnniversaryRepository.UpdatedAnniversary(
                value.title(), value.date(), value.description(), value.coverAssetId(), value.sortOrder()))) {
            throw ApiException.notFound("ANNIVERSARY_NOT_FOUND", "纪念日不存在或无权访问");
        }
        return item(space.internalId(),require(space.internalId(), anniversaryId));
    }

    @Transactional
    public void delete(UUID spaceId, UUID anniversaryId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        if (!anniversaries.softDelete(space.internalId(), anniversaryId, LocalDateTime.now(ZoneOffset.UTC))) {
            throw ApiException.notFound("ANNIVERSARY_NOT_FOUND", "纪念日不存在或无权访问");
        }
    }

    private Validated validate(UUID spacePublicId,long spaceId, long accountId, Command command,boolean elevated) {
        String title = command.title() == null ? "" : command.title().trim();
        if (title.isBlank()) throw ApiException.badRequest("ANNIVERSARY_TITLE_REQUIRED", "纪念日名称不能为空");
        if (title.length() > 100) throw ApiException.badRequest("ANNIVERSARY_TITLE_TOO_LONG", "纪念日名称不能超过100个字符");
        if (command.date() == null) throw ApiException.badRequest("ANNIVERSARY_DATE_REQUIRED", "请选择纪念日日期");
        String description = command.description() == null || command.description().isBlank()
                ? null : command.description().trim();
        Long coverId = null;
        if (command.coverAssetId() != null) {
            mediaAccess.require(spacePublicId,command.coverAssetId(), MediaAccessContext.direct(accountId,elevated));
            coverId = media.findByPublicIds(spaceId, List.of(command.coverAssetId()), accountId).stream()
                    .filter(asset -> "IMAGE".equals(asset.mediaType()))
                    .map(MediaAsset::internalId).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("COVER_MEDIA_INVALID", "封面图片不存在或不属于当前空间"));
        }
        return new Validated(title, command.date(), description, coverId, Math.max(0, command.sortOrder()));
    }

    private Anniversary require(long spaceId, UUID publicId) {
        return anniversaries.findByPublicId(spaceId, publicId)
                .orElseThrow(() -> ApiException.notFound("ANNIVERSARY_NOT_FOUND", "纪念日不存在或无权访问"));
    }

    private Item item(long spaceId,Anniversary value){
        MediaAsset cover=value.coverAssetId()==null?null:media.findByPublicIdsInSpace(spaceId,List.of(value.coverAssetId()))
                .stream().findFirst().orElse(null);
        return new Item(value,cover);
    }

    public record Command(String title, LocalDate date, String description, UUID coverAssetId, int sortOrder) {
    }

    public record Item(Anniversary anniversary,MediaAsset coverMedia){}

    private record Validated(String title, LocalDate date, String description, Long coverAssetId, int sortOrder) {
    }
}
