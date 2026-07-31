package com.langxi.babydiary.platform.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiExceptionHandlerTest {
    @Test
    void oversizedMultipartRequestUsesPayloadTooLargeProblem() {
        var response =
                new ApiExceptionHandler().uploadTooLarge(new MaxUploadSizeExceededException(1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "UPLOAD_TOO_LARGE");
        assertThat(response.getBody().getProperties()).containsKey("traceId");
    }

    @Test
    void uncaughtUniqueKeyRaceUsesConflictProblem() {
        var response =
                new ApiExceptionHandler().duplicateKey(new DuplicateKeyException("duplicate"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "RESOURCE_CONFLICT");
    }
}
