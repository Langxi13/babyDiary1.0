package com.langxi.babydiary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.common.CacheNames;
import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.AiAlbumCandidateVO;
import com.langxi.babydiary.dto.AiAlbumProposalRequestDTO;
import com.langxi.babydiary.dto.AiAlbumProposalVO;
import com.langxi.babydiary.dto.PhotoVO;
import com.langxi.babydiary.entity.AiAlbumProposal;
import com.langxi.babydiary.entity.Album;
import com.langxi.babydiary.entity.AlbumGroup;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.DiaryImage;
import com.langxi.babydiary.entity.Photo;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.AiAlbumProposalMapper;
import com.langxi.babydiary.mapper.AlbumMapper;
import com.langxi.babydiary.mapper.DiaryImageMapper;
import com.langxi.babydiary.mapper.DiaryMapper;
import com.langxi.babydiary.mapper.PhotoMapper;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AiAlbumProposalService {

    private static final int MAX_CONTENT_PER_DIARY = 800;
    private static final int MAX_INPUT_CHARS = 28000;

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private OpenAiCompatibleClient aiClient;

    @Autowired
    private DiaryMapper diaryMapper;

    @Autowired
    private DiaryImageMapper diaryImageMapper;

    @Autowired
    private AlbumMapper albumMapper;

    @Autowired
    private PhotoMapper photoMapper;

    @Autowired
    private AiAlbumProposalMapper proposalMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public AiAlbumProposalVO generate(Integer userId, AiAlbumProposalRequestDTO request) {
        Date startDate = Date.valueOf(request.getStartDate());
        Date endDate = Date.valueOf(request.getEndDate());
        if (endDate.before(startDate)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }

        List<Diary> diaries = diaryMapper.findDiariesForReport(userId, startDate, endDate);
        if (diaries.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_CONFIG_INVALID, "该时间段没有日记，无法整理相册");
        }
        List<Integer> diaryIds = diaries.stream().map(Diary::getDiaryId).collect(Collectors.toList());
        List<DiaryImage> images = diaryImageMapper.findDiaryImagesByDiaryIds(diaryIds);
        if (images.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_CONFIG_INVALID, "该时间段没有图片，无法整理相册");
        }

        Map<Integer, List<DiaryImage>> imagesByDiary = images.stream()
                .collect(Collectors.groupingBy(DiaryImage::getDiaryId, HashMap::new, Collectors.toList()));
        Map<Integer, Diary> diaryById = diaries.stream().collect(Collectors.toMap(Diary::getDiaryId, diary -> diary));
        List<Album> existingAiAlbums = albumMapper.findAiAlbumsByUserId(userId);
        Map<Integer, Album> aiAlbumById = existingAiAlbums.stream()
                .collect(Collectors.toMap(Album::getAlbumId, album -> album));

        AiRuntimeConfig config = aiConfigService.getRuntimeConfig();
        String aiResponse = aiClient.generate(config, Arrays.asList(
                new AiChatMessage("system", systemPrompt()),
                new AiChatMessage("user", userPrompt(startDate, endDate, request.getPrompt(), diaries, imagesByDiary, existingAiAlbums))
        ));
        List<AiAlbumCandidateVO> candidates = parseCandidates(aiResponse, diaryById, imagesByDiary, aiAlbumById);

        AiAlbumProposal proposal = new AiAlbumProposal();
        proposal.setUserId(userId);
        proposal.setStatus("PENDING");
        proposal.setStartDate(startDate);
        proposal.setEndDate(endDate);
        proposal.setPrompt(trimToNull(request.getPrompt()));
        proposal.setContentJson(writeContent(candidates));
        proposal.setModel(config.getModel());
        proposalMapper.insert(proposal);
        return AiAlbumProposalVO.fromEntity(proposal, enrichPhotos(userId, candidates));
    }

    public AiAlbumProposalVO findById(Integer userId, Integer proposalId) {
        AiAlbumProposal proposal = requireProposal(userId, proposalId);
        return AiAlbumProposalVO.fromEntity(proposal, enrichPhotos(userId, readContent(proposal.getContentJson())));
    }

    @Transactional
    public AiAlbumProposalVO update(Integer userId, Integer proposalId, AiAlbumProposalVO edited) {
        AiAlbumProposal proposal = requirePendingProposal(userId, proposalId);
        proposal.setContentJson(writeContent(sanitizeEditedCandidates(edited.getAlbums())));
        proposalMapper.updateContent(proposal);
        return findById(userId, proposalId);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.PHOTOS, allEntries = true)
    public AiAlbumProposalVO confirm(Integer userId, Integer proposalId) {
        AiAlbumProposal proposal = requirePendingProposal(userId, proposalId);
        List<AiAlbumCandidateVO> candidates = readContent(proposal.getContentJson());
        validateOwnedImages(userId, candidates);
        AlbumGroup aiGroup = ensureAiGroup(userId);
        for (AiAlbumCandidateVO candidate : candidates) {
            if (Boolean.TRUE.equals(candidate.getDiscarded()) || candidate.getImageIds() == null || candidate.getImageIds().isEmpty()) {
                continue;
            }
            Integer albumId;
            if ("MERGE".equals(candidate.getMode())) {
                Album target = albumMapper.findAlbumById(userId, candidate.getTargetAlbumId());
                if (target == null || !"AI".equals(target.getType())) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "AI只能合并到已有AI相册");
                }
                albumId = target.getAlbumId();
            } else {
                Album album = new Album();
                album.setUserId(userId);
                album.setGroupId(aiGroup.getGroupId());
                album.setType("AI");
                album.setName(truncate(trimRequired(candidate.getTitle(), "AI 相册"), 100));
                album.setDescription(trimToNull(truncate(candidate.getDescription(), 500)));
                album.setCoverImagePath(null);
                album.setSort(0);
                albumMapper.insertAlbum(album);
                albumId = album.getAlbumId();
            }
            albumMapper.insertAlbumPhotos(albumId, candidate.getImageIds().stream().distinct().collect(Collectors.toList()));
        }
        proposalMapper.updateStatus(userId, proposalId, "CONFIRMED");
        proposal.setStatus("CONFIRMED");
        return AiAlbumProposalVO.fromEntity(proposal, enrichPhotos(userId, candidates));
    }

    @Transactional
    public void discard(Integer userId, Integer proposalId) {
        requireProposal(userId, proposalId);
        proposalMapper.updateStatus(userId, proposalId, "REJECTED");
    }

    private List<AiAlbumCandidateVO> parseCandidates(String response, Map<Integer, Diary> diaryById,
                                                     Map<Integer, List<DiaryImage>> imagesByDiary,
                                                     Map<Integer, Album> aiAlbumById) {
        try {
            JsonNode albumsNode = objectMapper.readTree(response).path("albums");
            if (!albumsNode.isArray()) {
                throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI相册响应缺少albums数组");
            }
            List<AiAlbumCandidateVO> candidates = new ArrayList<>();
            for (JsonNode node : albumsNode) {
                AiAlbumCandidateVO candidate = new AiAlbumCandidateVO();
                String mode = node.path("mode").asText("NEW").trim().toUpperCase();
                candidate.setMode("MERGE".equals(mode) ? "MERGE" : "NEW");
                candidate.setTargetAlbumId(node.path("targetAlbumId").isInt() ? node.path("targetAlbumId").asInt() : null);
                if ("MERGE".equals(candidate.getMode())) {
                    Album target = aiAlbumById.get(candidate.getTargetAlbumId());
                    if (target == null) {
                        continue;
                    }
                    candidate.setTargetAlbumName(target.getName());
                }
                candidate.setTitle(truncate(node.path("title").asText("AI 整理相册"), 100));
                candidate.setDescription(truncate(node.path("description").asText(""), 500));
                candidate.setDiaryIds(validDiaryIds(node.path("diaryIds"), diaryById.keySet()));
                candidate.setImageIds(imageIdsForDiaries(candidate.getDiaryIds(), imagesByDiary));
                if (!candidate.getImageIds().isEmpty()) {
                    candidates.add(candidate);
                }
            }
            if (candidates.isEmpty()) {
                throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI没有生成可用相册");
            }
            return candidates;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI相册响应解析失败");
        }
    }

    private List<Integer> validDiaryIds(JsonNode diaryIdsNode, Set<Integer> allowedDiaryIds) {
        if (!diaryIdsNode.isArray()) {
            return Collections.emptyList();
        }
        List<Integer> ids = new ArrayList<>();
        for (JsonNode item : diaryIdsNode) {
            if (item.isInt() && allowedDiaryIds.contains(item.asInt())) {
                ids.add(item.asInt());
            }
        }
        return ids;
    }

    private List<Integer> imageIdsForDiaries(List<Integer> diaryIds, Map<Integer, List<DiaryImage>> imagesByDiary) {
        LinkedHashSet<Integer> imageIds = new LinkedHashSet<>();
        for (Integer diaryId : diaryIds) {
            for (DiaryImage image : imagesByDiary.getOrDefault(diaryId, Collections.emptyList())) {
                imageIds.add(image.getImageId());
            }
        }
        return new ArrayList<>(imageIds);
    }

    private List<AiAlbumCandidateVO> enrichPhotos(Integer userId, List<AiAlbumCandidateVO> candidates) {
        Set<Integer> allImageIds = new LinkedHashSet<>();
        for (AiAlbumCandidateVO candidate : candidates) {
            if (candidate.getImageIds() != null) {
                allImageIds.addAll(candidate.getImageIds());
            }
        }
        if (allImageIds.isEmpty()) {
            return candidates;
        }
        List<Photo> photos = photoMapper.findPhotosByIds(userId, new ArrayList<>(allImageIds));
        if (photos == null) {
            photos = Collections.emptyList();
        }
        Map<Integer, PhotoVO> photosById = photos.stream()
                .collect(Collectors.toMap(Photo::getImageId, PhotoVO::fromEntity));
        for (AiAlbumCandidateVO candidate : candidates) {
            List<PhotoVO> candidatePhotos = new ArrayList<>();
            for (Integer imageId : candidate.getImageIds()) {
                PhotoVO photo = photosById.get(imageId);
                if (photo != null) {
                    candidatePhotos.add(photo);
                }
            }
            candidate.setPhotos(candidatePhotos);
        }
        return candidates;
    }

    private List<AiAlbumCandidateVO> readContent(String contentJson) {
        try {
            JsonNode albumsNode = objectMapper.readTree(contentJson).path("albums");
            List<AiAlbumCandidateVO> candidates = new ArrayList<>();
            if (albumsNode.isArray()) {
                for (JsonNode node : albumsNode) {
                    candidates.add(objectMapper.treeToValue(node, AiAlbumCandidateVO.class));
                }
            }
            return candidates;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI相册提案解析失败");
        }
    }

    private List<AiAlbumCandidateVO> sanitizeEditedCandidates(List<AiAlbumCandidateVO> candidates) {
        if (candidates == null) {
            return Collections.emptyList();
        }
        List<AiAlbumCandidateVO> sanitized = new ArrayList<>();
        for (AiAlbumCandidateVO candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            AiAlbumCandidateVO copy = new AiAlbumCandidateVO();
            copy.setMode("MERGE".equals(candidate.getMode()) ? "MERGE" : "NEW");
            copy.setTargetAlbumId(candidate.getTargetAlbumId());
            copy.setTargetAlbumName(truncate(candidate.getTargetAlbumName(), 100));
            copy.setTitle(truncate(candidate.getTitle(), 100));
            copy.setDescription(truncate(candidate.getDescription(), 500));
            copy.setDiaryIds(normalizeIds(candidate.getDiaryIds(), 500));
            copy.setImageIds(normalizeIds(candidate.getImageIds(), 500));
            copy.setDiscarded(Boolean.TRUE.equals(candidate.getDiscarded()));
            sanitized.add(copy);
        }
        return sanitized;
    }

    private List<Integer> normalizeIds(List<Integer> ids, int limit) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void validateOwnedImages(Integer userId, List<AiAlbumCandidateVO> candidates) {
        Set<Integer> imageIds = candidates.stream()
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getDiscarded()))
                .flatMap(candidate -> candidate.getImageIds() == null
                        ? java.util.stream.Stream.empty()
                        : candidate.getImageIds().stream())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (imageIds.isEmpty()) {
            return;
        }
        if (imageIds.size() > 1000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次最多确认1000张照片");
        }
        List<Photo> ownedPhotos = photoMapper.findPhotosByIds(userId, new ArrayList<>(imageIds));
        Set<Integer> ownedImageIds = ownedPhotos == null
                ? Collections.emptySet()
                : ownedPhotos.stream().map(Photo::getImageId).collect(Collectors.toSet());
        if (!ownedImageIds.containsAll(imageIds)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "部分照片不存在或不属于当前用户");
        }
    }

    private String writeContent(List<AiAlbumCandidateVO> candidates) {
        try {
            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("albums", candidates == null ? Collections.emptyList() : candidates);
            return objectMapper.writeValueAsString(wrapper);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_REQUEST_FAILED, "AI相册提案保存失败");
        }
    }

    private AiAlbumProposal requireProposal(Integer userId, Integer proposalId) {
        AiAlbumProposal proposal = proposalMapper.findById(userId, proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.AI_ALBUM_PROPOSAL_NOT_FOUND);
        }
        return proposal;
    }

    private AiAlbumProposal requirePendingProposal(Integer userId, Integer proposalId) {
        AiAlbumProposal proposal = requireProposal(userId, proposalId);
        if (!"PENDING".equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该AI相册提案已处理");
        }
        return proposal;
    }

    private AlbumGroup ensureAiGroup(Integer userId) {
        AlbumGroup group = albumMapper.ensureAiGroup(userId);
        if (group != null) {
            return group;
        }
        AlbumGroup created = new AlbumGroup();
        created.setUserId(userId);
        created.setType("AI");
        created.setName("AI 整理");
        created.setSort(10);
        albumMapper.insertGroup(created);
        return created;
    }

    private String systemPrompt() {
        return "你是 Baby Diary 的相册整理助手。你只能基于用户提供的日记文字、日期、标签、心情和图片数量进行整理，不要编造不存在的事件。只输出严格JSON，不要输出Markdown。JSON格式：{\"albums\":[{\"mode\":\"NEW或MERGE\",\"targetAlbumId\":已有AI相册ID或null,\"title\":\"相册名\",\"description\":\"一句描述\",\"diaryIds\":[日记ID]}]}。只能按日记ID聚合，不要直接指定图片。";
    }

    private String userPrompt(Date startDate, Date endDate, String prompt, List<Diary> diaries,
                              Map<Integer, List<DiaryImage>> imagesByDiary, List<Album> existingAiAlbums) {
        StringBuilder builder = new StringBuilder();
        builder.append("整理时间段：").append(startDate).append(" 至 ").append(endDate).append("\n");
        if (prompt != null && !prompt.trim().isEmpty()) {
            builder.append("用户提示：").append(prompt.trim()).append("\n");
        }
        builder.append("已有AI相册，只允许MERGE到这些相册：\n");
        for (Album album : existingAiAlbums) {
            builder.append("- albumId=").append(album.getAlbumId()).append("，名称=").append(album.getName()).append("\n");
        }
        builder.append("日记列表：\n");
        for (Diary diary : diaries) {
            String item = "- diaryId=" + diary.getDiaryId()
                    + "，日期=" + diary.getDate()
                    + "，标题=" + nullToEmpty(diary.getTitle())
                    + "，心情=" + nullToEmpty(diary.getMoodKey())
                    + "，图片数量=" + imagesByDiary.getOrDefault(diary.getDiaryId(), Collections.emptyList()).size()
                    + "\n  内容=" + truncate(toPlainText(diary.getContent()), MAX_CONTENT_PER_DIARY)
                    + "\n";
            if (builder.length() + item.length() > MAX_INPUT_CHARS) {
                builder.append("[后续日记因长度限制已省略]\n");
                break;
            }
            builder.append(item);
        }
        return builder.toString();
    }

    private String toPlainText(String html) {
        if (html == null || html.trim().isEmpty()) {
            return "";
        }
        return Jsoup.parse(html).text();
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private String trimRequired(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
