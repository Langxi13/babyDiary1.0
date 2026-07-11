package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class V2ExceptionHandlerTest {

    private final V2ExceptionHandler handler = new V2ExceptionHandler();

    @Test
    void missingMediaUsesNotFoundStatus() {
        ProblemDetail problem = handler.handleBusiness(new BusinessException(ErrorCode.FILE_NOT_FOUND));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("资源不存在");
    }
}
