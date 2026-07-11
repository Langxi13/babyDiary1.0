package com.langxi.babydiary.service;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.dto.SearchResponseVO;
import com.langxi.babydiary.dto.SearchResultVO;
import com.langxi.babydiary.entity.DiarySpace;
import com.langxi.babydiary.exception.BusinessException;
import com.langxi.babydiary.mapper.SearchMapper;
import org.jsoup.Jsoup;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SearchService {
    private final SearchMapper mapper;
    private final SpaceService spaceService;

    public SearchService(SearchMapper mapper, SpaceService spaceService) {
        this.mapper = mapper;
        this.spaceService = spaceService;
    }

    public SearchResponseVO search(String spacePublicId, Integer userId, String query, int limit) {
        DiarySpace space = spaceService.requireSpace(spacePublicId);
        spaceService.requireMember(space, userId);
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "搜索内容长度应为1到100个字符");
        }
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        List<SearchResultVO> results = normalized.codePointCount(0, normalized.length()) < 2
                ? mapper.searchLike(space.getSpaceId(), userId, normalized, normalizedLimit)
                : mapper.searchFullText(space.getSpaceId(), userId, normalized, normalizedLimit);
        results.forEach(result -> result.setSnippet(Jsoup.parse(result.getSnippet() == null ? "" : result.getSnippet()).text()));
        return new SearchResponseVO(normalized, false, results);
    }

    public void indexDiary(Integer diaryId) {
        mapper.deleteDiaryById(diaryId);
        mapper.upsertDiary(diaryId);
    }

    public void removeDiary(String publicId) {
        mapper.deleteDiary(publicId);
    }

    @Scheduled(cron = "${app.search.refresh-cron:0 */10 * * * *}")
    @Transactional
    public void refreshIndex() {
        mapper.refreshDiaryDocuments();
        mapper.removeStaleDiaryDocuments();
    }
}
