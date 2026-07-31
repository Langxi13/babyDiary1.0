package com.langxi.babydiary.ai.infrastructure;

import com.langxi.babydiary.ai.application.AiAlbumProposalRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAiAlbumProposalRepository implements AiAlbumProposalRepository {
    private final AiAlbumProposalMapper mapper;

    public MyBatisAiAlbumProposalRepository(AiAlbumProposalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long insertProposal(
            byte[] publicId,
            long spaceId,
            long createdBy,
            LocalDate startDate,
            LocalDate endDate,
            String prompt,
            String model) {
        AiAlbumProposalMapper.ProposalInsert row =
                new AiAlbumProposalMapper.ProposalInsert(
                        publicId, spaceId, createdBy, startDate, endDate, prompt, model);
        mapper.insertProposal(row);
        return row.getProposalId();
    }

    @Override
    public ProposalData findProposal(long spaceId, long accountId, byte[] publicId) {
        AiAlbumProposalMapper.ProposalRow row = mapper.findProposal(spaceId, accountId, publicId);
        return row == null
                ? null
                : new ProposalData(
                        row.getProposalId(),
                        row.getPublicId(),
                        row.getStatus(),
                        row.getStartDate(),
                        row.getEndDate(),
                        row.getPrompt(),
                        row.getModel(),
                        row.getCreatedAt(),
                        row.getUpdatedAt());
    }

    @Override
    public int updateStatus(long proposalId, String status) {
        return mapper.updateStatus(proposalId, status);
    }

    @Override
    public long insertCandidate(
            long spaceId,
            long proposalId,
            String mode,
            Long targetAlbumId,
            String title,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            boolean discarded,
            int position) {
        AiAlbumProposalMapper.CandidateInsert row =
                new AiAlbumProposalMapper.CandidateInsert(
                        spaceId,
                        proposalId,
                        mode,
                        targetAlbumId,
                        title,
                        description,
                        startDate,
                        endDate,
                        discarded,
                        position);
        mapper.insertCandidate(row);
        return row.getCandidateId();
    }

    @Override
    public List<CandidateData> findCandidates(long proposalId) {
        return mapper.findCandidates(proposalId).stream()
                .map(
                        row ->
                                new CandidateData(
                                        row.getCandidateId(),
                                        row.getMode(),
                                        row.getTitle(),
                                        row.getDescription(),
                                        row.isDiscarded(),
                                        row.getTargetAlbumPublicId(),
                                        row.getTargetAlbumName()))
                .toList();
    }

    @Override
    public void deleteCandidates(long proposalId) {
        mapper.deleteCandidates(proposalId);
    }

    @Override
    public void insertCandidateDiary(long spaceId, long candidateId, long diaryId, int position) {
        mapper.insertCandidateDiary(spaceId, candidateId, diaryId, position);
    }

    @Override
    public void insertCandidateMedia(long spaceId, long candidateId, long assetId, int position) {
        mapper.insertCandidateMedia(spaceId, candidateId, assetId, position);
    }

    @Override
    public List<byte[]> findCandidateDiaries(long candidateId, long accountId) {
        return mapper.findCandidateDiaries(candidateId, accountId);
    }

    @Override
    public List<byte[]> findCandidateMedia(long candidateId, long accountId) {
        return mapper.findCandidateMedia(candidateId, accountId);
    }

    @Override
    public List<DiaryMedia> findDiaryMedia(
            long spaceId, long accountId, LocalDate startDate, LocalDate endDate) {
        return mapper.findDiaryMedia(spaceId, accountId, startDate, endDate).stream()
                .map(
                        row ->
                                new DiaryMedia(
                                        row.diaryId(),
                                        row.diaryPublicId(),
                                        row.assetId(),
                                        row.assetPublicId()))
                .toList();
    }

    @Override
    public List<IdReference> resolveDiaries(long spaceId, long accountId, List<byte[]> ids) {
        return mapper.resolveDiaries(spaceId, accountId, ids).stream()
                .map(row -> new IdReference(row.internalId(), row.publicId()))
                .toList();
    }

    @Override
    public List<IdReference> resolveMedia(
            long spaceId, long accountId, List<byte[]> diaryIds, List<byte[]> ids) {
        return mapper.resolveMedia(spaceId, accountId, diaryIds, ids).stream()
                .map(row -> new IdReference(row.internalId(), row.publicId()))
                .toList();
    }
}
