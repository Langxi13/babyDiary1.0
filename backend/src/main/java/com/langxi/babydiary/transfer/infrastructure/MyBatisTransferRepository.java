package com.langxi.babydiary.transfer.infrastructure;

import com.langxi.babydiary.transfer.application.TransferRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisTransferRepository implements TransferRepository {
    private final TransferMapper mapper;

    public MyBatisTransferRepository(TransferMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String findSpaceName(long spaceId) {
        return mapper.findSpaceName(spaceId);
    }

    @Override
    public List<DiaryData> findDiaries(
            long spaceId, long accountId, LocalDate startDate, LocalDate endDate, int limit) {
        return mapper.findDiaries(spaceId, accountId, startDate, endDate, limit).stream()
                .map(
                        row ->
                                new DiaryData(
                                        row.diaryId(),
                                        row.publicId(),
                                        row.title(),
                                        row.diaryDate(),
                                        row.contentHtml(),
                                        row.moodKey(),
                                        row.visibility(),
                                        row.locked()))
                .toList();
    }

    @Override
    public List<TagData> findTags(List<Long> diaryIds) {
        return mapper.findTags(diaryIds).stream()
                .map(row -> new TagData(row.diaryId(), row.name(), row.color()))
                .toList();
    }

    @Override
    public List<MediaData> findMedia(List<Long> diaryIds) {
        return mapper.findMedia(diaryIds).stream()
                .map(
                        row ->
                                new MediaData(
                                        row.diaryId(),
                                        row.publicId(),
                                        row.originalFilename(),
                                        row.mediaType(),
                                        row.caption(),
                                        row.takenAt(),
                                        row.position()))
                .toList();
    }

    @Override
    public List<CommentData> findComments(List<Long> diaryIds) {
        return mapper.findComments(diaryIds).stream()
                .map(
                        row ->
                                new CommentData(
                                        row.diaryId(),
                                        row.username(),
                                        row.content(),
                                        row.createdAt()))
                .toList();
    }

    @Override
    public boolean diaryExists(long spaceId, byte[] publicId) {
        return mapper.countDiary(spaceId, publicId) > 0;
    }
}
