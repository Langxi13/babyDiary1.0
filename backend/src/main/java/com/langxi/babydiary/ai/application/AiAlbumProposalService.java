package com.langxi.babydiary.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.ai.infrastructure.AiAlbumProposalMapper;
import com.langxi.babydiary.album.application.AlbumRepository;
import com.langxi.babydiary.album.application.AlbumService;
import com.langxi.babydiary.media.application.MediaRepository;
import com.langxi.babydiary.media.domain.MediaAsset;
import com.langxi.babydiary.platform.application.BinaryUuid;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.space.application.SpaceAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiAlbumProposalService {
    private final SpaceAccess spaces;
    private final AiConfigService configs;
    private final AiClient client;
    private final AiReportRepository reports;
    private final MediaRepository media;
    private final AlbumRepository albumRepository;
    private final AlbumService albums;
    private final AiAlbumProposalMapper mapper;
    private final ObjectMapper json;

    public AiAlbumProposalService(SpaceAccess spaces, AiConfigService configs, AiClient client,
                                  AiReportRepository reports, MediaRepository media,
                                  AlbumRepository albumRepository, AlbumService albums,
                                  AiAlbumProposalMapper mapper, ObjectMapper json) {
        this.spaces = spaces;
        this.configs = configs;
        this.client = client;
        this.reports = reports;
        this.media = media;
        this.albumRepository = albumRepository;
        this.albums = albums;
        this.mapper = mapper;
        this.json = json;
    }

    @Transactional
    public Proposal generate(UUID spaceId, long accountId, LocalDate start, LocalDate end, String prompt) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        validatePeriod(start, end);
        List<AiReportRepository.DiaryInput> diaryInputs = reports.findDiaries(
                space.internalId(), accountId, start, end);
        if (diaryInputs.isEmpty()) {
            throw ApiException.badRequest("AI_ALBUM_NO_DIARIES", "该时间段没有日记，无法整理相册");
        }
        List<AiAlbumProposalMapper.DiaryMediaRow> links = mapper.findDiaryMedia(
                space.internalId(), accountId, start, end);
        if (links.isEmpty()) {
            throw ApiException.badRequest("AI_ALBUM_NO_IMAGES", "该时间段没有图片，无法整理相册");
        }
        AiRuntimeConfig config = configs.runtime();
        String raw = client.generate(config, List.of(
                new AiClient.Message("system", systemPrompt()),
                new AiClient.Message("user", userPrompt(space.internalId(), start, end, prompt, diaryInputs, links))));
        List<CandidateCommand> commands = parse(raw, diaryInputs, links);
        UUID proposalId = UUID.randomUUID();
        AiAlbumProposalMapper.ProposalInsert insert = new AiAlbumProposalMapper.ProposalInsert(
                BinaryUuid.toBytes(proposalId), space.internalId(), accountId, start, end,
                trim(prompt, 1000), config.model());
        mapper.insertProposal(insert);
        replaceCandidates(space.internalId(),accountId, insert.getProposalId(), start, end, commands);
        return detail(spaceId, accountId, proposalId);
    }

    public Proposal detail(UUID spaceId, long accountId, UUID proposalId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        AiAlbumProposalMapper.ProposalRow proposal = require(space.internalId(), accountId, proposalId);
        List<Candidate> candidates = mapper.findCandidates(proposal.getProposalId()).stream()
                .map(row -> candidate(space.internalId(), accountId, row)).toList();
        return response(proposal, candidates);
    }

    @Transactional
    public Proposal update(UUID spaceId, long accountId, UUID proposalId, List<CandidateCommand> commands) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        AiAlbumProposalMapper.ProposalRow proposal = requirePending(space.internalId(), accountId, proposalId);
        mapper.deleteCandidates(proposal.getProposalId());
        replaceCandidates(space.internalId(),accountId, proposal.getProposalId(), proposal.getStartDate(),
                proposal.getEndDate(), commands);
        return detail(spaceId, accountId, proposalId);
    }

    @Transactional
    public Proposal confirm(UUID spaceId, long accountId, UUID proposalId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        AiAlbumProposalMapper.ProposalRow proposal = requirePending(space.internalId(), accountId, proposalId);
        for (AiAlbumProposalMapper.CandidateRow row : mapper.findCandidates(proposal.getProposalId())) {
            if (row.isDiscarded()) continue;
            List<UUID> assetIds = uuids(mapper.findCandidateMedia(row.getCandidateId()));
            if (assetIds.isEmpty()) continue;
            if ("MERGE".equals(row.getMode())) {
                UUID targetId = row.getTargetAlbumPublicId() == null
                        ? null : BinaryUuid.fromBytes(row.getTargetAlbumPublicId());
                if (targetId == null) {
                    throw ApiException.badRequest("AI_ALBUM_TARGET_INVALID", "目标 AI 相册不存在");
                }
                AlbumRepository.AlbumRow target = albumRepository.findAlbum(space.internalId(), targetId)
                        .filter(album -> "AI".equals(album.type()))
                        .orElseThrow(() -> ApiException.badRequest("AI_ALBUM_TARGET_INVALID", "目标 AI 相册不存在"));
                albums.addMedia(spaceId, target.id(), accountId, assetIds, false);
            } else {
                albums.createAiAlbum(spaceId, accountId, row.getTitle(), row.getDescription(), assetIds);
            }
        }
        if (mapper.updateStatus(proposal.getProposalId(), "CONFIRMED") != 1) throw stateChanged();
        return detail(spaceId, accountId, proposalId);
    }

    @Transactional
    public void dismiss(UUID spaceId, long accountId, UUID proposalId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        AiAlbumProposalMapper.ProposalRow proposal = requirePending(space.internalId(), accountId, proposalId);
        if (mapper.updateStatus(proposal.getProposalId(), "DISMISSED") != 1) throw stateChanged();
    }

    private Candidate candidate(long spaceId, long accountId, AiAlbumProposalMapper.CandidateRow row) {
        List<UUID> diaryIds = uuids(mapper.findCandidateDiaries(row.getCandidateId()));
        List<UUID> assetIds = uuids(mapper.findCandidateMedia(row.getCandidateId()));
        List<MediaAsset> photos = media.findByPublicIdsInSpace(spaceId, assetIds);
        UUID targetId = row.getTargetAlbumPublicId() == null
                ? null : BinaryUuid.fromBytes(row.getTargetAlbumPublicId());
        return new Candidate(row.getMode(), targetId, row.getTargetAlbumName(), row.getTitle(),
                row.getDescription(), diaryIds, assetIds, photos, row.isDiscarded());
    }

    private void replaceCandidates(long spaceId,long accountId, long proposalId, LocalDate start, LocalDate end,
                                   List<CandidateCommand> commands) {
        if (commands == null || commands.isEmpty() || commands.size() > 50) {
            throw ApiException.badRequest("AI_ALBUM_CANDIDATES_INVALID", "相册推荐不能为空且最多包含50项");
        }
        int position = 0;
        for (CandidateCommand command : commands) {
            String mode = "MERGE".equalsIgnoreCase(command.mode()) ? "MERGE" : "NEW";
            Long targetId = null;
            if ("MERGE".equals(mode)) {
                if (command.targetAlbumId() == null) {
                    throw ApiException.badRequest("AI_ALBUM_TARGET_INVALID", "请选择要合并的 AI 相册");
                }
                targetId = albumRepository.findAlbum(spaceId, command.targetAlbumId())
                        .filter(album -> "AI".equals(album.type()))
                        .map(AlbumRepository.AlbumRow::internalId)
                        .orElseThrow(() -> ApiException.badRequest("AI_ALBUM_TARGET_INVALID", "目标 AI 相册不存在"));
            }
            List<UUID> diaryIds = distinct(command.diaryIds(), 500);
            List<UUID> assetIds = distinct(command.assetIds(), 500);
            Map<UUID, Long> diaryRefs = diaryIds.isEmpty()
                    ? Map.of() : resolve(mapper.resolveDiaries(spaceId,accountId, bytes(diaryIds)));
            Map<UUID, Long> assetRefs = assetIds.isEmpty()||diaryIds.isEmpty()
                    ? Map.of() : resolve(mapper.resolveMedia(spaceId,accountId,bytes(diaryIds), bytes(assetIds)));
            if (diaryRefs.size() != diaryIds.size() || assetRefs.size() != assetIds.size()) {
                throw ApiException.badRequest("AI_ALBUM_REFS_INVALID", "部分日记或图片不属于当前空间");
            }
            AiAlbumProposalMapper.CandidateInsert row = new AiAlbumProposalMapper.CandidateInsert(
                    spaceId, proposalId, mode, targetId, requiredTitle(command.title()),
                    trim(command.description(), 500), start, end, command.discarded(), position++);
            mapper.insertCandidate(row);
            for (int index = 0; index < diaryIds.size(); index++) {
                mapper.insertCandidateDiary(spaceId, row.getCandidateId(), diaryRefs.get(diaryIds.get(index)), index);
            }
            for (int index = 0; index < assetIds.size(); index++) {
                mapper.insertCandidateMedia(spaceId, row.getCandidateId(), assetRefs.get(assetIds.get(index)), index);
            }
        }
    }

    private List<CandidateCommand> parse(String raw, List<AiReportRepository.DiaryInput> diaries,
                                         List<AiAlbumProposalMapper.DiaryMediaRow> links) {
        try {
            JsonNode nodes = json.readTree(raw).path("albums");
            if (!nodes.isArray()) throw new IllegalArgumentException();
            Map<UUID, List<UUID>> mediaByDiary = new LinkedHashMap<>();
            links.forEach(link -> mediaByDiary
                    .computeIfAbsent(BinaryUuid.fromBytes(link.diaryPublicId()), ignored -> new ArrayList<>())
                    .add(BinaryUuid.fromBytes(link.assetPublicId())));
            Map<UUID, Boolean> allowed = new LinkedHashMap<>();
            diaries.forEach(diary -> allowed.put(diary.id(), true));
            List<CandidateCommand> result = new ArrayList<>();
            for (JsonNode node : nodes) {
                List<UUID> diaryIds = new ArrayList<>();
                node.path("diaryIds").forEach(value -> {
                    try {
                        UUID id = UUID.fromString(value.asText());
                        if (allowed.containsKey(id)) diaryIds.add(id);
                    } catch (RuntimeException ignored) {
                    }
                });
                LinkedHashSet<UUID> assetIds = new LinkedHashSet<>();
                diaryIds.forEach(id -> assetIds.addAll(mediaByDiary.getOrDefault(id, List.of())));
                if (assetIds.isEmpty()) continue;
                result.add(new CandidateCommand(node.path("mode").asText("NEW"),
                        uuidOrNull(node.path("targetAlbumId").asText(null)),
                        node.path("title").asText("AI 整理相册"),
                        node.path("description").asText(null), diaryIds,
                        new ArrayList<>(assetIds), false));
            }
            if (result.isEmpty()) throw new IllegalArgumentException();
            return result;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_ALBUM_RESPONSE_INVALID", "AI 相册响应格式无效");
        }
    }

    private String userPrompt(long spaceId, LocalDate start, LocalDate end, String prompt,
                              List<AiReportRepository.DiaryInput> diaries,
                              List<AiAlbumProposalMapper.DiaryMediaRow> links) {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        links.forEach(link -> counts.merge(BinaryUuid.fromBytes(link.diaryPublicId()), 1L, Long::sum));
        StringBuilder value = new StringBuilder("整理周期：").append(start).append(" 至 ").append(end).append('\n');
        if (prompt != null && !prompt.isBlank()) value.append("用户提示：").append(trim(prompt, 500)).append('\n');
        value.append("可合并的已有 AI 相册：\n");
        albumRepository.findAlbums(spaceId).stream().filter(album -> "AI".equals(album.type())).forEach(album ->
                value.append("- albumId=").append(album.id()).append(" 名称=").append(album.name()).append('\n'));
        value.append("日记：\n");
        for (AiReportRepository.DiaryInput diary : diaries) {
            value.append("- diaryId=").append(diary.id()).append(" 日期=").append(diary.date())
                    .append(" 标题=").append(diary.title()).append(" 图片数=")
                    .append(counts.getOrDefault(diary.id(), 0L)).append(" 内容=")
                    .append(trim(diary.contentText(), 800)).append('\n');
            if (value.length() > 28_000) break;
        }
        return value.toString();
    }

    private String systemPrompt() {
        return "你是相册整理助手，只能根据给定日记分组，不能编造事实。只输出严格 JSON："
                + "{\"albums\":[{\"mode\":\"NEW或MERGE\",\"targetAlbumId\":\"已有AI相册UUID或null\","
                + "\"title\":\"相册名\",\"description\":\"描述\",\"diaryIds\":[\"日记UUID\"]}]}。";
    }

    private AiAlbumProposalMapper.ProposalRow require(long spaceId, long accountId, UUID proposalId) {
        AiAlbumProposalMapper.ProposalRow row = mapper.findProposal(
                spaceId, accountId, BinaryUuid.toBytes(proposalId));
        if (row == null) throw ApiException.notFound("AI_ALBUM_PROPOSAL_NOT_FOUND", "AI 相册提案不存在或无权访问");
        return row;
    }
    private AiAlbumProposalMapper.ProposalRow requirePending(long spaceId, long accountId, UUID proposalId) {
        AiAlbumProposalMapper.ProposalRow row = require(spaceId, accountId, proposalId);
        if (!"PENDING".equals(row.getStatus())) throw stateChanged();
        return row;
    }
    private Proposal response(AiAlbumProposalMapper.ProposalRow row, List<Candidate> candidates) {
        return new Proposal(BinaryUuid.fromBytes(row.getPublicId()), row.getStatus(), row.getStartDate(),
                row.getEndDate(), row.getPrompt(), row.getModel(), candidates, row.getCreatedAt(), row.getUpdatedAt());
    }
    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw ApiException.badRequest("DATE_RANGE_INVALID", "结束日期不能早于开始日期");
        }
    }
    private ApiException stateChanged() {
        return new ApiException(HttpStatus.CONFLICT, "AI_ALBUM_STATE_CHANGED", "AI 相册提案已处理");
    }
    private List<byte[]> bytes(List<UUID> values) { return values.stream().map(BinaryUuid::toBytes).toList(); }
    private List<UUID> uuids(List<byte[]> values) { return values.stream().map(BinaryUuid::fromBytes).toList(); }
    private Map<UUID, Long> resolve(List<AiAlbumProposalMapper.IdRow> rows) {
        Map<UUID, Long> values = new LinkedHashMap<>();
        rows.forEach(row -> values.put(BinaryUuid.fromBytes(row.publicId()), row.internalId()));
        return values;
    }
    private List<UUID> distinct(List<UUID> values, int max) {
        List<UUID> result = values == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(values));
        if (result.size() > max) throw ApiException.badRequest("TOO_MANY_REFS", "关联数量过多");
        return result;
    }
    private String requiredTitle(String value) {
        String title = trim(value, 100);
        if (title == null) throw ApiException.badRequest("AI_ALBUM_TITLE_REQUIRED", "相册名称不能为空");
        return title;
    }
    private String trim(String value, int max) {
        if (value == null || value.trim().isEmpty()) return null;
        String result = value.trim();
        return result.length() <= max ? result : result.substring(0, max);
    }
    private UUID uuidOrNull(String value) {
        try { return value == null || value.isBlank() ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public record Proposal(UUID proposalId, String status, LocalDate startDate, LocalDate endDate,
                           String prompt, String model, List<Candidate> albums,
                           LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record Candidate(String mode, UUID targetAlbumId, String targetAlbumName, String title,
                            String description, List<UUID> diaryIds, List<UUID> assetIds,
                            List<MediaAsset> photos, boolean discarded) {}
    public record CandidateCommand(String mode, UUID targetAlbumId, String title, String description,
                                   List<UUID> diaryIds, List<UUID> assetIds, boolean discarded) {}
}
