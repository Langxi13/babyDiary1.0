package com.langxi.babydiary.draft.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.diary.application.DiaryRepository;
import com.langxi.babydiary.draft.domain.DiaryDraft;
import com.langxi.babydiary.platform.application.ApiException;
import com.langxi.babydiary.space.application.SpaceAccess;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftService {
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final int MAX_PAYLOAD_BYTES = 1_000_000;
    private final SpaceAccess spaces;
    private final DraftRepository drafts;
    private final DiaryRepository diaries;
    private final ObjectMapper json;

    public DraftService(
            SpaceAccess spaces,
            DraftRepository drafts,
            DiaryRepository diaries,
            ObjectMapper json) {
        this.spaces = spaces;
        this.drafts = drafts;
        this.diaries = diaries;
        this.json = json;
    }

    public List<DiaryDraft> list(UUID spaceId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return drafts.findForOwner(space.internalId(), accountId).stream()
                .map(this::toDraft)
                .toList();
    }

    public DiaryDraft detail(UUID spaceId, String draftKey, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        validateKey(draftKey);
        return drafts.findByKey(space.internalId(), accountId, draftKey)
                .map(this::toDraft)
                .orElseThrow(() -> ApiException.notFound("DRAFT_NOT_FOUND", "草稿不存在"));
    }

    @Transactional
    public DiaryDraft save(
            UUID spaceId, String draftKey, long accountId, UUID diaryId, JsonNode payload) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        validateKey(draftKey);
        if (payload == null || payload.isNull() || !payload.isObject()) {
            throw ApiException.badRequest("DRAFT_PAYLOAD_INVALID", "草稿内容必须是 JSON 对象");
        }
        String serialized;
        try {
            serialized = json.writeValueAsString(payload);
        } catch (Exception exception) {
            throw ApiException.badRequest("DRAFT_PAYLOAD_INVALID", "草稿内容无法序列化");
        }
        if (serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > MAX_PAYLOAD_BYTES) {
            throw ApiException.badRequest("DRAFT_TOO_LARGE", "草稿内容过大");
        }
        Long internalDiaryId = null;
        if (diaryId != null) {
            internalDiaryId =
                    diaries.findByPublicId(space.internalId(), diaryId, accountId, false)
                            .map(com.langxi.babydiary.diary.domain.DiaryEntry::internalId)
                            .orElseThrow(
                                    () ->
                                            ApiException.badRequest(
                                                    "DIARY_NOT_FOUND", "关联日记不存在或不属于当前空间"));
        }
        drafts.upsert(
                new DraftRepository.NewDraft(
                        UUID.randomUUID(),
                        space.internalId(),
                        accountId,
                        internalDiaryId,
                        draftKey,
                        serialized));
        return detail(spaceId, draftKey, accountId);
    }

    @Transactional
    public void delete(UUID spaceId, String draftKey, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        validateKey(draftKey);
        drafts.delete(space.internalId(), accountId, draftKey);
    }

    private DiaryDraft toDraft(DraftRepository.Row row) {
        try {
            return new DiaryDraft(
                    row.id(),
                    row.spaceId(),
                    row.draftKey(),
                    row.diaryId(),
                    json.readTree(row.payloadJson()),
                    row.createdAt(),
                    row.updatedAt());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored draft JSON is invalid", exception);
        }
    }

    private void validateKey(String value) {
        if (value == null || !KEY.matcher(value).matches()) {
            throw ApiException.badRequest("DRAFT_KEY_INVALID", "草稿标识格式无效");
        }
    }
}
