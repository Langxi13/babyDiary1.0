package com.langxi.babydiary.exception;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void missingStaticResourcesReturnNotFoundInsteadOfInternalError() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Result<Void> result = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "v3/api-docs"));

        assertThat(result.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        ResponseStatus status = GlobalExceptionHandler.class
                .getMethod("handleNoResourceFoundException", NoResourceFoundException.class)
                .getAnnotation(ResponseStatus.class);
        assertThat(status.value()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
