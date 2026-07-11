package com.langxi.babydiary.service;

import com.langxi.babydiary.dto.DiaryWriteDTO;
import com.langxi.babydiary.dto.SyncOperationDTO;
import com.langxi.babydiary.dto.SyncOperationResultVO;
import com.langxi.babydiary.entity.DiarySpace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncOperationExecutorTest {
    @Mock private CollaborativeDiaryService diaryService;
    @Mock private SyncResultStore resultStore;

    @InjectMocks private SyncOperationExecutor executor;

    @Test
    void transientRuntimeFailureIsRetryableAndNotStoredAsPermanentResult() {
        DiarySpace space = new DiarySpace();
        space.setSpaceId(7L);
        space.setPublicId("space-one");
        SyncOperationDTO operation = new SyncOperationDTO();
        operation.setOperationId("operation-one");
        operation.setEntityType("DIARY");
        operation.setAction("CREATE");
        operation.setEntityId("4de4a477-c96f-45ab-b8ae-74f7ec53c129");
        operation.setPayload(new DiaryWriteDTO());
        when(diaryService.create("space-one", 2, operation.getPayload(), null))
                .thenThrow(new IllegalStateException("temporary database outage"));

        SyncOperationResultVO result = executor.execute(space, 2, operation, null);

        assertThat(result.getStatus()).isEqualTo("RETRYABLE");
        verify(resultStore, never()).save(any(), any(), any(), any());
    }
}
