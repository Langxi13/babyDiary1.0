package com.langxi.babydiary.home.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.home.application.HomeProjection;
import com.langxi.babydiary.home.application.HomeProjectionRepository;
import com.langxi.babydiary.platform.application.BinaryUuid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisHomeProjectionRepository implements HomeProjectionRepository {
    private final HomeMapper mapper;
    private final ObjectMapper json;

    public MyBatisHomeProjectionRepository(HomeMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public HomeProjection load(long spaceId, long accountId, boolean elevated) {
        List<HomeMapper.DiaryRow> diaryRows = mapper.findRecentDiaries(spaceId, accountId);
        long diaryTotal = diaryRows.isEmpty() ? 0 : diaryRows.get(0).diaryTotal();
        List<HomeProjection.Diary> diaries = hydrateDiaries(diaryRows, elevated);
        List<HomeProjection.Draft> drafts =
                mapper.findDrafts(spaceId, accountId).stream().map(this::draft).toList();
        List<HomeProjection.Anniversary> anniversaries =
                mapper.findAnniversaries(spaceId).stream()
                        .map(
                                row ->
                                        new HomeProjection.Anniversary(
                                                uuid(row.publicId()),
                                                row.title(),
                                                row.anniversaryDate()))
                        .toList();
        List<HomeProjection.Favorite> favorites =
                mapper.findFavorites(spaceId, accountId, elevated).stream()
                        .map(
                                row ->
                                        new HomeProjection.Favorite(
                                                uuid(row.publicId()),
                                                row.mediaType(),
                                                row.status(),
                                                row.protectedContent(),
                                                row.originalProfile(),
                                                row.thumbnailProfile(),
                                                row.previewProfile()))
                        .toList();
        return new HomeProjection(diaryTotal, diaries, drafts, anniversaries, favorites);
    }

    private List<HomeProjection.Diary> hydrateDiaries(
            List<HomeMapper.DiaryRow> rows, boolean elevated) {
        Map<Long, DiaryBuilder> grouped = new LinkedHashMap<>();
        for (HomeMapper.DiaryRow row : rows) {
            DiaryBuilder diary =
                    grouped.computeIfAbsent(row.diaryId(), ignored -> new DiaryBuilder(row));
            if (row.tagPublicId() != null) {
                UUID id = uuid(row.tagPublicId());
                if (diary.tagIds.add(id)) {
                    diary.tags.add(new HomeProjection.Tag(id, row.tagName(), row.tagColor()));
                }
            }
            if (row.mediaPublicId() != null) {
                UUID id = uuid(row.mediaPublicId());
                if (diary.mediaIds.add(id)) {
                    diary.previews.add(
                            new HomeProjection.Media(
                                    id,
                                    row.mediaType(),
                                    row.mediaPosition() == null ? 0 : row.mediaPosition(),
                                    row.mediaStatus(),
                                    row.protectedContent(),
                                    row.originalProfile(),
                                    row.thumbnailProfile(),
                                    row.previewProfile()));
                }
            }
        }
        return grouped.values().stream().map(value -> value.build(elevated)).toList();
    }

    private HomeProjection.Draft draft(HomeMapper.DraftRow row) {
        try {
            return new HomeProjection.Draft(
                    uuid(row.publicId()),
                    row.draftKey(),
                    row.diaryPublicId() == null ? null : uuid(row.diaryPublicId()),
                    json.readTree(row.payloadJson()),
                    row.updatedAt());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored draft JSON is invalid", exception);
        }
    }

    private UUID uuid(byte[] value) {
        return BinaryUuid.fromBytes(value);
    }

    private static final class DiaryBuilder {
        private final HomeMapper.DiaryRow row;
        private final List<HomeProjection.Tag> tags = new ArrayList<>();
        private final Set<UUID> tagIds = new LinkedHashSet<>();
        private final List<HomeProjection.Media> previews = new ArrayList<>();
        private final Set<UUID> mediaIds = new LinkedHashSet<>();

        private DiaryBuilder(HomeMapper.DiaryRow row) {
            this.row = row;
        }

        private HomeProjection.Diary build(boolean elevated) {
            boolean masked = row.locked() && !elevated;
            return new HomeProjection.Diary(
                    BinaryUuid.fromBytes(row.diaryPublicId()),
                    masked ? null : row.title(),
                    row.diaryDate(),
                    masked ? null : row.contentSnippet(),
                    masked ? null : row.mood(),
                    row.visibility(),
                    row.locked(),
                    row.version(),
                    row.createdAt(),
                    row.updatedAt(),
                    masked ? List.of() : List.copyOf(tags),
                    masked ? 0 : row.mediaCount(),
                    masked ? List.of() : List.copyOf(previews));
        }
    }
}
