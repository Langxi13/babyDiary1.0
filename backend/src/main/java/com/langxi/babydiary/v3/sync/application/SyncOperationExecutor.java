package com.langxi.babydiary.v3.sync.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.langxi.babydiary.v3.diary.application.DiaryService;
import com.langxi.babydiary.v3.diary.domain.DiaryEntry;
import com.langxi.babydiary.v3.platform.application.V3Exception;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SyncOperationExecutor {
    private final DiaryService diaries;
    private final SyncRepository operations;

    public SyncOperationExecutor(DiaryService diaries, SyncRepository operations) {
        this.diaries = diaries; this.operations = operations;
    }

    public Result execute(UUID spaceId, long internalSpaceId, long accountId,boolean elevated, Operation operation) {
        SyncRepository.OperationResult previous = operations.findOperation(
                operation.operationId(), accountId, internalSpaceId);
        if (previous != null) {
            return new Result(operation.operationId(), previous.resultCode(), previous.entityId(), null, null, null);
        }
        Result result;
        try {
            result = apply(spaceId, accountId,elevated, operation);
        } catch (V3Exception exception) {
            if(exception.status()==HttpStatus.LOCKED){
                return new Result(operation.operationId(),"RETRYABLE",operation.entityId(),operation.baseVersion(),
                        exception.code(),exception.getMessage());
            }
            String status = exception.status() == HttpStatus.PRECONDITION_FAILED ? "CONFLICT" : "FAILED";
            result = new Result(operation.operationId(), status, operation.entityId(), operation.baseVersion(),
                    exception.code(), exception.getMessage());
        } catch (RuntimeException exception) {
            return new Result(operation.operationId(), "RETRYABLE", operation.entityId(), operation.baseVersion(),
                    "SYNC_RETRYABLE", "同步操作暂时无法完成");
        }
        if (!operations.insertOperation(operation.operationId(), accountId, internalSpaceId, result.status(),
                "DIARY", result.entityId(), LocalDateTime.now(ZoneOffset.UTC).plusDays(30))) {
            SyncRepository.OperationResult concurrent = operations.findOperation(
                    operation.operationId(), accountId, internalSpaceId);
            if (concurrent != null) {
                return new Result(operation.operationId(), concurrent.resultCode(), concurrent.entityId(), null, null, null);
            }
        }
        return result;
    }

    private Result apply(UUID spaceId, long accountId,boolean elevated, Operation operation) {
        DiaryEntry diary = switch (operation.action()) {
            case "CREATE" -> diaries.create(spaceId, accountId, command(operation.entityId(), operation.payload()));
            case "UPDATE" -> diaries.update(spaceId, requiredEntity(operation), accountId,
                    requiredVersion(operation), command(null, operation.payload()),elevated);
            case "DELETE" -> {
                diaries.moveToTrash(spaceId, requiredEntity(operation), accountId, requiredVersion(operation),elevated);
                yield null;
            }
            case "RESTORE" -> diaries.restore(spaceId, requiredEntity(operation), accountId, requiredVersion(operation),elevated);
            default -> throw V3Exception.badRequest("SYNC_ACTION_INVALID", "同步动作无效");
        };
        UUID entityId = diary == null ? operation.entityId() : diary.id();
        Integer version = diary == null ? requiredVersion(operation) + 1 : diary.version();
        return new Result(operation.operationId(), "APPLIED", entityId, version, null, null);
    }

    private DiaryService.Command command(UUID clientId, JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw V3Exception.badRequest("SYNC_PAYLOAD_REQUIRED", "同步操作缺少日记内容");
        }
        String date = text(payload, "diaryDate", text(payload, "date", null));
        if (date == null) throw V3Exception.badRequest("DIARY_DATE_REQUIRED", "请选择日记日期");
        return new DiaryService.Command(clientId, text(payload, "title", null), LocalDate.parse(date),
                text(payload, "contentHtml", text(payload, "content", "")),
                text(payload, "mood", text(payload, "moodKey", null)),
                text(payload, "visibility", "PRIVATE"), payload.path("locked").asBoolean(false),
                uuids(payload.path("tagIds")), uuids(payload.path("mediaIds")));
    }

    private UUID requiredEntity(Operation operation) {
        if (operation.entityId() == null) throw V3Exception.badRequest("SYNC_ENTITY_REQUIRED", "同步操作缺少日记标识");
        return operation.entityId();
    }
    private int requiredVersion(Operation operation) {
        if (operation.baseVersion() == null || operation.baseVersion() < 1) {
            throw V3Exception.badRequest("SYNC_VERSION_REQUIRED", "同步操作缺少有效版本");
        }
        return operation.baseVersion();
    }
    private List<UUID> uuids(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<UUID> values = new ArrayList<>();
        node.forEach(value -> { try { values.add(UUID.fromString(value.asText())); } catch (RuntimeException ignored) {} });
        return values;
    }
    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    public record Operation(UUID operationId, String entityType, String action, UUID entityId,
                            Integer baseVersion, JsonNode payload) {}
    public record Result(UUID operationId, String status, UUID entityId, Integer version,
                         String errorCode, String message) {}
}
