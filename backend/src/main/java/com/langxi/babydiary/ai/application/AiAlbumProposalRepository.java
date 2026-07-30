package com.langxi.babydiary.ai.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AiAlbumProposalRepository {
    long insertProposal(
            byte[] publicId,
            long spaceId,
            long createdBy,
            LocalDate startDate,
            LocalDate endDate,
            String prompt,
            String model);

    ProposalData findProposal(long spaceId, long accountId, byte[] publicId);

    int updateStatus(long proposalId, String status);

    long insertCandidate(
            long spaceId,
            long proposalId,
            String mode,
            Long targetAlbumId,
            String title,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            boolean discarded,
            int position);

    List<CandidateData> findCandidates(long proposalId);

    void deleteCandidates(long proposalId);

    void insertCandidateDiary(long spaceId, long candidateId, long diaryId, int position);

    void insertCandidateMedia(long spaceId, long candidateId, long assetId, int position);

    List<byte[]> findCandidateDiaries(long candidateId);

    List<byte[]> findCandidateMedia(long candidateId);

    List<DiaryMedia> findDiaryMedia(
            long spaceId, long accountId, LocalDate startDate, LocalDate endDate);

    List<IdReference> resolveDiaries(long spaceId, long accountId, List<byte[]> ids);

    List<IdReference> resolveMedia(
            long spaceId, long accountId, List<byte[]> diaryIds, List<byte[]> ids);

    record ProposalData(
            long proposalId,
            byte[] publicId,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String prompt,
            String model,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    record CandidateData(
            long candidateId,
            String mode,
            String title,
            String description,
            boolean discarded,
            byte[] targetAlbumPublicId,
            String targetAlbumName) {}

    record DiaryMedia(long diaryId, byte[] diaryPublicId, long assetId, byte[] assetPublicId) {}

    record IdReference(long internalId, byte[] publicId) {}
}
