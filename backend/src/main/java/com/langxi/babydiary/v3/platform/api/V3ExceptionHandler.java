package com.langxi.babydiary.v3.platform.api;

import com.langxi.babydiary.v3.platform.application.V3Exception;
import com.langxi.babydiary.v3.media.api.MediaRangeException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice(basePackages = "com.langxi.babydiary.v3")
public class V3ExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(V3ExceptionHandler.class);

    @ExceptionHandler(V3Exception.class)
    ResponseEntity<ProblemDetail> business(V3Exception exception) {
        return problem(exception.status(), exception.code(), exception.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> constraint(ConstraintViolationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> malformed(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "请求内容无法解析", null);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ProblemDetail> requestShape(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "REQUEST_INVALID", "请求参数或媒体类型无效", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限执行该操作", null);
    }

    @ExceptionHandler(MediaRangeException.class)
    ResponseEntity<ProblemDetail> range(MediaRangeException exception) {
        ProblemDetail detail = base(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                "MEDIA_RANGE_INVALID", "请求的媒体范围无效", traceId());
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(org.springframework.http.HttpHeaders.CONTENT_RANGE, "bytes */" + exception.total())
                .body(detail);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        String traceId = traceId();
        log.error("V3 unhandled error traceId={}", traceId, exception);
        ProblemDetail detail = base(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误", traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(detail);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String message,
                                                   Map<String, String> errors) {
        ProblemDetail detail = base(status, code, message, traceId());
        if (errors != null && !errors.isEmpty()) detail.setProperty("errors", errors);
        return ResponseEntity.status(status).body(detail);
    }

    private ProblemDetail base(HttpStatus status, String code, String message, String traceId) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setType(URI.create("urn:baby-diary:problem:" + code.toLowerCase().replace('_', '-')));
        detail.setProperty("code", code);
        detail.setProperty("traceId", traceId);
        return detail;
    }

    private String traceId() {
        return UUID.randomUUID().toString();
    }
}
