package com.langxi.babydiary.diary.application;

import com.langxi.babydiary.diary.domain.DiarySummary;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.platform.domain.CursorPage;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DiarySummaryService {
    private final SpaceAccess spaces;
    private final DiaryRepository diaries;

    public DiarySummaryService(SpaceAccess spaces, DiaryRepository diaries) {
        this.spaces = spaces;
        this.diaries = diaries;
    }

    public CursorPage<DiarySummary> list(
            UUID spaceId, long accountId, DiaryService.ListQuery query, boolean elevated) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        Cursor cursor = decodeCursor(query.cursor());
        int size = Math.max(1, Math.min(query.size(), 50));
        if (query.startDate() != null
                && query.endDate() != null
                && query.endDate().isBefore(query.startDate())) {
            throw ApiException.badRequest("DATE_RANGE_INVALID", "结束日期不能早于开始日期");
        }
        DiaryRepository.Query repositoryQuery =
                new DiaryRepository.Query(
                        space.internalId(),
                        accountId,
                        query.startDate(),
                        query.endDate(),
                        normalizeKeyword(query.keyword()),
                        blankToNull(query.mood()),
                        query.tagId(),
                        query.trash(),
                        elevated,
                        cursor == null ? null : cursor.date(),
                        cursor == null ? null : cursor.id(),
                        size + 1);
        List<DiarySummary> rows = new ArrayList<>(diaries.findSummaryPage(repositoryQuery));
        Long total =
                query.includeTotal()
                        ? diaries.count(
                                new DiaryRepository.Query(
                                        repositoryQuery.spaceId(),
                                        repositoryQuery.accountId(),
                                        repositoryQuery.startDate(),
                                        repositoryQuery.endDate(),
                                        repositoryQuery.keyword(),
                                        repositoryQuery.mood(),
                                        repositoryQuery.tagId(),
                                        repositoryQuery.trash(),
                                        repositoryQuery.elevated(),
                                        null,
                                        null,
                                        1))
                        : null;
        String next = null;
        if (rows.size() > size) {
            rows.remove(rows.size() - 1);
            DiarySummary last = rows.get(rows.size() - 1);
            next = encodeCursor(last.diaryDate(), last.internalId());
        }
        if (!elevated) rows.replaceAll(this::protectLocked);
        return new CursorPage<>(rows, next, total);
    }

    private DiarySummary protectLocked(DiarySummary diary) {
        if (!diary.locked()) return diary;
        return new DiarySummary(
                diary.internalId(),
                diary.id(),
                diary.spaceId(),
                diary.authorId(),
                null,
                diary.diaryDate(),
                null,
                null,
                diary.visibility(),
                true,
                diary.version(),
                diary.createdAt(),
                diary.updatedAt(),
                diary.deletedAt(),
                List.of(),
                0,
                List.of());
    }

    private Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String decoded =
                    new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            return new Cursor(LocalDate.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception exception) {
            throw ApiException.badRequest("CURSOR_INVALID", "分页游标无效");
        }
    }

    private String encodeCursor(LocalDate date, long id) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((date + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeKeyword(String value) {
        String keyword = blankToNull(value);
        if (keyword != null && keyword.length() > 200) {
            throw ApiException.badRequest("KEYWORD_TOO_LONG", "搜索关键字不能超过200个字符");
        }
        return keyword;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private record Cursor(LocalDate date, long id) {}
}
