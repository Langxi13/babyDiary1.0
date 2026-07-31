package com.langxi.babydiary.diary.infrastructure;

import com.langxi.babydiary.diary.application.DiaryRepository;
import com.langxi.babydiary.diary.domain.DiaryEntry;
import com.langxi.babydiary.platform.application.BinaryUuid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisDiaryRepository implements DiaryRepository {
    private final DiaryMapper mapper;

    public MyBatisDiaryRepository(DiaryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<DiaryEntry> findPage(Query query) {
        byte[] tag = query.tagId() == null ? null : BinaryUuid.toBytes(query.tagId());
        return hydrate(mapper.findPage(query, tag));
    }

    @Override
    public long count(Query query) {
        byte[] tag = query.tagId() == null ? null : BinaryUuid.toBytes(query.tagId());
        return mapper.count(query, tag);
    }

    @Override
    public Optional<DiaryEntry> findByPublicId(
            long spaceId, UUID diaryId, long accountId, boolean includeDeleted) {
        DiaryMapper.DiaryRow row =
                mapper.findByPublicId(
                        spaceId, BinaryUuid.toBytes(diaryId), accountId, includeDeleted);
        if (row == null) return Optional.empty();
        return hydrate(List.of(row)).stream().findFirst();
    }

    @Override
    public long insert(NewDiary diary) {
        DiaryMapper.DiaryInsert row =
                new DiaryMapper.DiaryInsert(
                        BinaryUuid.toBytes(diary.publicId()),
                        diary.spaceId(),
                        diary.authorId(),
                        diary.title(),
                        diary.diaryDate(),
                        diary.contentHtml(),
                        diary.contentText(),
                        diary.mood(),
                        diary.visibility(),
                        diary.locked());
        mapper.insert(row);
        if (row.getDiaryId() == null)
            throw new IllegalStateException("Diary insert returned no ID");
        return row.getDiaryId();
    }

    @Override
    public int update(long diaryId, int expectedVersion, UpdatedDiary diary) {
        return mapper.update(diaryId, expectedVersion, diary);
    }

    @Override
    public int setDeleted(long diaryId, int expectedVersion, LocalDateTime deletedAt) {
        return mapper.setDeleted(diaryId, expectedVersion, deletedAt);
    }

    @Override
    public boolean permanentlyDelete(long diaryId, int expectedVersion) {
        if (mapper.lockDeleted(diaryId, expectedVersion) == null) return false;
        mapper.deleteReportLinks(diaryId);
        mapper.deleteProposalLinks(diaryId);
        return mapper.permanentlyDelete(diaryId, expectedVersion) == 1;
    }

    @Override
    public List<PurgeCandidate> findPurgeCandidates(LocalDateTime deletedBefore, int limit) {
        return mapper.findPurgeCandidates(deletedBefore, limit).stream()
                .map(
                        row ->
                                new PurgeCandidate(
                                        row.diaryId(),
                                        BinaryUuid.fromBytes(row.publicId()),
                                        row.spaceId(),
                                        BinaryUuid.fromBytes(row.spacePublicId()),
                                        row.authorId(),
                                        row.visibility(),
                                        row.version()))
                .toList();
    }

    @Override
    public List<Long> resolveTagIds(long spaceId, List<UUID> publicIds) {
        return resolve(
                publicIds,
                publicIds.isEmpty()
                        ? List.of()
                        : mapper.resolveTagIds(
                                spaceId, publicIds.stream().map(BinaryUuid::toBytes).toList()));
    }

    @Override
    public List<Long> resolveMediaIds(
            long spaceId, long accountId, boolean locked, List<UUID> publicIds) {
        return resolve(
                publicIds,
                publicIds.isEmpty()
                        ? List.of()
                        : mapper.resolveMediaIds(
                                spaceId,
                                accountId,
                                locked,
                                publicIds.stream().map(BinaryUuid::toBytes).toList()));
    }

    @Override
    public void replaceTags(long spaceId, long diaryId, List<Long> tagIds) {
        mapper.deleteTags(diaryId);
        tagIds.forEach(tagId -> mapper.insertTag(spaceId, diaryId, tagId));
    }

    @Override
    public void replaceMedia(long spaceId, long diaryId, List<Long> assetIds) {
        mapper.deleteMedia(diaryId);
        for (int index = 0; index < assetIds.size(); index++) {
            mapper.insertMedia(spaceId, diaryId, assetIds.get(index), index);
        }
    }

    @Override
    public void insertRevision(
            long diaryId,
            int version,
            long editorId,
            String snapshotJson,
            LocalDateTime createdAt) {
        mapper.insertRevision(diaryId, version, editorId, snapshotJson, createdAt);
    }

    @Override
    public Optional<Revision> findRevision(long diaryId, UUID revisionId) {
        return Optional.ofNullable(mapper.findRevision(diaryId, BinaryUuid.toBytes(revisionId)))
                .map(this::revision);
    }

    @Override
    public List<RevisionSummary> findRevisions(long diaryId) {
        return mapper.findRevisions(diaryId).stream()
                .map(
                        row ->
                                new RevisionSummary(
                                        BinaryUuid.fromBytes(row.publicId()),
                                        row.version(),
                                        BinaryUuid.fromBytes(row.editorPublicId()),
                                        row.editorName(),
                                        row.createdAt()))
                .toList();
    }

    private Revision revision(DiaryMapper.RevisionRow row) {
        return new Revision(
                BinaryUuid.fromBytes(row.publicId()),
                row.version(),
                BinaryUuid.fromBytes(row.editorPublicId()),
                row.editorName(),
                row.snapshotJson(),
                row.createdAt());
    }

    private List<DiaryEntry> hydrate(List<DiaryMapper.DiaryRow> rows) {
        if (rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(DiaryMapper.DiaryRow::diaryId).toList();
        Map<Long, List<DiaryEntry.TagRef>> tags = new HashMap<>();
        for (DiaryMapper.TagRow row : mapper.findTags(ids)) {
            tags.computeIfAbsent(row.diaryId(), ignored -> new ArrayList<>())
                    .add(
                            new DiaryEntry.TagRef(
                                    BinaryUuid.fromBytes(row.publicId()), row.name(), row.color()));
        }
        Map<Long, List<DiaryEntry.MediaRef>> media = new HashMap<>();
        for (DiaryMapper.MediaRow row : mapper.findMedia(ids)) {
            media.computeIfAbsent(row.diaryId(), ignored -> new ArrayList<>())
                    .add(
                            new DiaryEntry.MediaRef(
                                    BinaryUuid.fromBytes(row.publicId()),
                                    row.mediaType(),
                                    row.caption(),
                                    row.takenAt(),
                                    row.position(),
                                    row.status(),
                                    row.originalProfile(),
                                    row.thumbnailProfile(),
                                    row.previewProfile(),
                                    row.protectedContent()));
        }
        return rows.stream()
                .map(
                        row ->
                                new DiaryEntry(
                                        row.diaryId(),
                                        BinaryUuid.fromBytes(row.publicId()),
                                        BinaryUuid.fromBytes(row.spacePublicId()),
                                        row.authorId(),
                                        row.title(),
                                        row.diaryDate(),
                                        row.contentHtml(),
                                        row.contentText(),
                                        row.moodKey(),
                                        row.visibility(),
                                        row.locked(),
                                        row.version(),
                                        row.createdAt(),
                                        row.updatedAt(),
                                        row.deletedAt(),
                                        tags.getOrDefault(row.diaryId(), List.of()),
                                        media.getOrDefault(row.diaryId(), List.of())))
                .toList();
    }

    private List<Long> resolve(List<UUID> requested, List<DiaryMapper.IdRow> rows) {
        Map<UUID, Long> byPublicId = new LinkedHashMap<>();
        rows.forEach(row -> byPublicId.put(BinaryUuid.fromBytes(row.publicId()), row.internalId()));
        return requested.stream().map(byPublicId::get).filter(java.util.Objects::nonNull).toList();
    }
}
