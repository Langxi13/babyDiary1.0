package com.langxi.babydiary.v3.diary.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.langxi.babydiary.v3.diary.domain.DiaryEntry;
import com.langxi.babydiary.v3.platform.application.ChangeRecorder;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.platform.domain.CursorPage;
import com.langxi.babydiary.v3.space.application.SpaceAccess;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DiaryService {
    private static final int MAX_TITLE = 255;
    private static final int MAX_HTML = 1_000_000;
    private static final int MAX_REFS = 50;
    private final SpaceAccess spaces;
    private final DiaryRepository diaries;
    private final DiaryContentPolicy contentPolicy;
    private final ChangeRecorder changes;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public DiaryService(SpaceAccess spaces, DiaryRepository diaries, DiaryContentPolicy contentPolicy,
                        ChangeRecorder changes, ObjectMapper json) {
        this(spaces, diaries, contentPolicy, changes, json, Clock.systemUTC());
    }

    DiaryService(SpaceAccess spaces, DiaryRepository diaries, DiaryContentPolicy contentPolicy,
                 ChangeRecorder changes, ObjectMapper json, Clock clock) {
        this.spaces = spaces;
        this.diaries = diaries;
        this.contentPolicy = contentPolicy;
        this.changes = changes;
        this.json = json;
        this.clock = clock;
    }

    public CursorPage<DiaryEntry> list(UUID spaceId, long accountId, ListQuery query) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        Cursor cursor = decodeCursor(query.cursor());
        int size = Math.max(1, Math.min(query.size(), 50));
        if (query.startDate() != null && query.endDate() != null && query.endDate().isBefore(query.startDate())) {
            throw V3Exception.badRequest("DATE_RANGE_INVALID", "结束日期不能早于开始日期");
        }
        DiaryRepository.Query repositoryQuery = new DiaryRepository.Query(
                space.internalId(), accountId, query.startDate(), query.endDate(), normalizeKeyword(query.keyword()),
                blankToNull(query.mood()), query.tagId(), query.trash(), cursor == null ? null : cursor.date(),
                cursor == null ? null : cursor.id(), size + 1);
        List<DiaryEntry> rows = new ArrayList<>(diaries.findPage(repositoryQuery));
        long totalElements = diaries.count(new DiaryRepository.Query(repositoryQuery.spaceId(), repositoryQuery.accountId(),
                repositoryQuery.startDate(), repositoryQuery.endDate(), repositoryQuery.keyword(), repositoryQuery.mood(),
                repositoryQuery.tagId(), repositoryQuery.trash(), null, null, 1));
        String nextCursor = null;
        if (rows.size() > size) {
            rows.remove(rows.size() - 1);
            DiaryEntry last = rows.get(rows.size() - 1);
            nextCursor = encodeCursor(last.diaryDate(), last.internalId());
        }
        return new CursorPage<>(rows, nextCursor, totalElements);
    }

    public DiaryEntry detail(UUID spaceId, UUID diaryId, long accountId, boolean includeDeleted) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        return requireDiary(space.internalId(), diaryId, accountId, includeDeleted);
    }

    @Transactional
    public DiaryEntry create(UUID spaceId, long accountId, Command command) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        Validated validated = validate(command, space);
        List<Long> tagIds = resolveTags(space.internalId(), validated.tagIds());
        List<Long> mediaIds = resolveMedia(space.internalId(), validated.mediaIds());
        UUID publicId = command.clientId() == null ? UUID.randomUUID() : command.clientId();
        if (command.clientId() != null) {
            DiaryEntry existing = diaries.findByPublicId(space.internalId(), publicId, accountId, false).orElse(null);
            if (existing != null) {
                if (existing.authorId() != accountId) {
                    throw V3Exception.conflict("DIARY_CLIENT_ID_CONFLICT", "日记客户端标识已被使用");
                }
                return existing;
            }
        }
        long diaryId = diaries.insert(new DiaryRepository.NewDiary(publicId, space.internalId(), accountId,
                validated.title(), validated.diaryDate(), validated.content().html(), validated.content().text(),
                validated.mood(), validated.visibility(), validated.locked()));
        diaries.replaceTags(space.internalId(), diaryId, tagIds);
        diaries.replaceMedia(space.internalId(), diaryId, mediaIds);
        DiaryEntry created = requireDiary(space.internalId(), publicId, accountId, false);
        diaries.insertRevision(diaryId, created.version(), accountId, snapshot(created), now());
        changes.record(space.internalId(), accountId, "DIARY", publicId,
                "DIARY_CREATED", created.version(), Map.of());
        return created;
    }

    @Transactional
    public DiaryEntry update(UUID spaceId, UUID diaryId, long accountId, int expectedVersion, Command command) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        DiaryEntry current = requireDiary(space.internalId(), diaryId, accountId, false);
        if (current.version() != expectedVersion) throw versionMismatch();
        Validated validated = validate(command, space);
        List<Long> tagIds = resolveTags(space.internalId(), validated.tagIds());
        List<Long> mediaIds = resolveMedia(space.internalId(), validated.mediaIds());
        int updated = diaries.update(current.internalId(), expectedVersion,
                new DiaryRepository.UpdatedDiary(validated.title(), validated.diaryDate(), validated.content().html(),
                        validated.content().text(), validated.mood(), validated.visibility(), validated.locked()));
        if (updated != 1) throw versionMismatch();
        diaries.replaceTags(space.internalId(), current.internalId(), tagIds);
        diaries.replaceMedia(space.internalId(), current.internalId(), mediaIds);
        DiaryEntry result = requireDiary(space.internalId(), diaryId, accountId, false);
        diaries.insertRevision(result.internalId(), result.version(), accountId, snapshot(result), now());
        changes.record(space.internalId(), accountId, "DIARY", diaryId,
                "DIARY_UPDATED", result.version(), Map.of());
        return result;
    }

    @Transactional
    public void moveToTrash(UUID spaceId, UUID diaryId, long accountId, int expectedVersion) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        DiaryEntry current = requireDiary(space.internalId(), diaryId, accountId, false);
        if (current.version() != expectedVersion
                || diaries.setDeleted(current.internalId(), expectedVersion, now()) != 1) throw versionMismatch();
        changes.record(space.internalId(), accountId, "DIARY", diaryId,
                "DIARY_DELETED", expectedVersion + 1, Map.of());
    }

    @Transactional
    public DiaryEntry restore(UUID spaceId, UUID diaryId, long accountId, int expectedVersion) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        DiaryEntry current = requireDiary(space.internalId(), diaryId, accountId, true);
        if (current.deletedAt() == null) return current;
        if (current.version() != expectedVersion
                || diaries.setDeleted(current.internalId(), expectedVersion, null) != 1) throw versionMismatch();
        DiaryEntry result = requireDiary(space.internalId(), diaryId, accountId, false);
        changes.record(space.internalId(), accountId, "DIARY", diaryId,
                "DIARY_RESTORED", result.version(), Map.of());
        return result;
    }

    public List<DiaryRepository.RevisionSummary> revisions(UUID spaceId, UUID diaryId, long accountId) {
        SpaceAccess.SpaceContext space = spaces.requireMember(spaceId, accountId);
        DiaryEntry diary = requireDiary(space.internalId(), diaryId, accountId, true);
        return diaries.findRevisions(diary.internalId());
    }

    @Transactional
    public DiaryEntry restoreRevision(UUID spaceId, UUID diaryId, long revisionId, long accountId,
                                      int expectedVersion) {
        SpaceAccess.SpaceContext space = spaces.requireWriter(spaceId, accountId);
        DiaryEntry current = requireDiary(space.internalId(), diaryId, accountId, false);
        DiaryRepository.Revision revision = diaries.findRevision(current.internalId(), revisionId)
                .orElseThrow(() -> V3Exception.notFound("REVISION_NOT_FOUND", "历史版本不存在"));
        try {
            JsonNode snapshot = json.readTree(revision.snapshotJson());
            Command command = new Command(null, snapshot.path("title").asText(),
                    LocalDate.parse(snapshot.path("diaryDate").asText()), snapshot.path("contentHtml").asText(),
                    textOrNull(snapshot, "mood"), snapshot.path("visibility").asText("PRIVATE"),
                    snapshot.path("locked").asBoolean(false), uuids(snapshot.path("tagIds")),
                    uuids(snapshot.path("mediaIds")));
            return update(spaceId, diaryId, accountId, expectedVersion, command);
        } catch (V3Exception exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Stored diary revision is invalid", exception);
        }
    }

    private Validated validate(Command command, SpaceAccess.SpaceContext space) {
        String title = command.title() == null ? "" : command.title().trim();
        if (title.isBlank()) throw V3Exception.badRequest("DIARY_TITLE_REQUIRED", "日记标题不能为空");
        if (title.length() > MAX_TITLE) throw V3Exception.badRequest("DIARY_TITLE_TOO_LONG", "日记标题不能超过255个字符");
        if (command.diaryDate() == null) throw V3Exception.badRequest("DIARY_DATE_REQUIRED", "请选择日记日期");
        if (command.contentHtml() != null && command.contentHtml().length() > MAX_HTML) {
            throw V3Exception.badRequest("DIARY_CONTENT_TOO_LONG", "日记内容过长");
        }
        DiaryContentPolicy.Content content = contentPolicy.normalize(command.contentHtml());
        String visibility = "PERSONAL".equals(space.type()) ? "PRIVATE"
                : ("PRIVATE".equals(command.visibility()) ? "PRIVATE" : "SHARED");
        List<UUID> tags = distinct(command.tagIds());
        List<UUID> media = distinct(command.mediaIds());
        if (tags.size() > MAX_REFS) throw V3Exception.badRequest("TOO_MANY_TAGS", "单篇日记最多选择50个标签");
        if (media.size() > MAX_REFS) throw V3Exception.badRequest("TOO_MANY_MEDIA", "单篇日记最多关联50个媒体");
        return new Validated(title, command.diaryDate(), content, blankToNull(command.mood()), visibility,
                command.locked(), tags, media);
    }

    private List<Long> resolveTags(long spaceId, List<UUID> values) {
        List<Long> result = diaries.resolveTagIds(spaceId, values);
        if (result.size() != values.size()) throw V3Exception.badRequest("TAG_NOT_FOUND", "部分标签不存在或不属于当前空间");
        return result;
    }

    private List<Long> resolveMedia(long spaceId, List<UUID> values) {
        List<Long> result = diaries.resolveMediaIds(spaceId, values);
        if (result.size() != values.size()) throw V3Exception.badRequest("MEDIA_NOT_FOUND", "部分媒体不存在或不属于当前空间");
        return result;
    }

    private DiaryEntry requireDiary(long spaceId, UUID diaryId, long accountId, boolean includeDeleted) {
        return diaries.findByPublicId(spaceId, diaryId, accountId, includeDeleted)
                .orElseThrow(() -> V3Exception.notFound("DIARY_NOT_FOUND", "日记不存在或无权访问"));
    }

    private String snapshot(DiaryEntry diary) {
        ObjectNode root = json.createObjectNode();
        root.put("title", diary.title());
        root.put("diaryDate", diary.diaryDate().toString());
        root.put("contentHtml", diary.contentHtml());
        root.put("contentText", diary.contentText());
        if (diary.mood() == null) root.putNull("mood"); else root.put("mood", diary.mood());
        root.put("visibility", diary.visibility());
        root.put("locked", diary.locked());
        ArrayNode tags = root.putArray("tagIds");
        diary.tags().forEach(tag -> tags.add(tag.id().toString()));
        ArrayNode media = root.putArray("mediaIds");
        diary.media().forEach(asset -> media.add(asset.id().toString()));
        try {
            return json.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize diary revision", exception);
        }
    }

    private Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            return new Cursor(LocalDate.parse(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception exception) {
            throw V3Exception.badRequest("CURSOR_INVALID", "分页游标无效");
        }
    }

    private String encodeCursor(LocalDate date, long id) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((date + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeKeyword(String value) {
        String keyword = blankToNull(value);
        if (keyword != null && keyword.length() > 200) {
            throw V3Exception.badRequest("KEYWORD_TOO_LONG", "搜索关键字不能超过200个字符");
        }
        return keyword;
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private List<UUID> distinct(List<UUID> values) {
        return values == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(values));
    }

    private List<UUID> uuids(JsonNode node) {
        List<UUID> result = new ArrayList<>();
        if (node.isArray()) node.forEach(value -> result.add(UUID.fromString(value.asText())));
        return result;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private V3Exception versionMismatch() {
        return new V3Exception(HttpStatus.PRECONDITION_FAILED, "DIARY_VERSION_MISMATCH",
                "日记已在其他设备更新，请刷新后重试");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public record Command(UUID clientId, String title, LocalDate diaryDate, String contentHtml, String mood,
                          String visibility, boolean locked, List<UUID> tagIds, List<UUID> mediaIds) {
    }

    public record ListQuery(LocalDate startDate, LocalDate endDate, String keyword, String mood,
                            UUID tagId, boolean trash, String cursor, int size) {
    }

    private record Validated(String title, LocalDate diaryDate, DiaryContentPolicy.Content content,
                             String mood, String visibility, boolean locked, List<UUID> tagIds,
                             List<UUID> mediaIds) {
    }

    private record Cursor(LocalDate date, long id) {
    }
}
