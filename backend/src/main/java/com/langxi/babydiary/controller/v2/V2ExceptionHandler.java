package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.ErrorCode;
import com.langxi.babydiary.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.langxi.babydiary.controller.v2")
public class V2ExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException exception) {
        HttpStatus status = statusFor(exception.getCode());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle(titleFor(status));
        problem.setType(URI.create("urn:baby-diary:error:" + exception.getCode()));
        problem.setProperty("code", exception.getCode());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage).collect(Collectors.joining(", "));
        return validationProblem(detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException exception) {
        String detail = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage()).collect(Collectors.joining(", "));
        return validationProblem(detail);
    }

    private ProblemDetail validationProblem(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("请求参数错误");
        problem.setType(URI.create("urn:baby-diary:error:400"));
        problem.setProperty("code", ErrorCode.BAD_REQUEST.getCode());
        return problem;
    }

    private HttpStatus statusFor(Integer code) {
        if (code == null) return HttpStatus.BAD_REQUEST;
        if (code == 401 || code == ErrorCode.UNAUTHORIZED.getCode()
                || code == ErrorCode.TOKEN_EXPIRED.getCode() || code == ErrorCode.TOKEN_INVALID.getCode()) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == 403 || code == ErrorCode.FORBIDDEN.getCode()
                || code == ErrorCode.SPACE_ACCESS_DENIED.getCode() || code == ErrorCode.SPACE_OWNER_REQUIRED.getCode()) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == 404 || code == ErrorCode.NOT_FOUND.getCode()
                || code == ErrorCode.USER_NOT_FOUND.getCode()
                || code == ErrorCode.DIARY_NOT_FOUND.getCode()
                || code == ErrorCode.SPACE_NOT_FOUND.getCode()
                || code == ErrorCode.FILE_NOT_FOUND.getCode()
                || code == ErrorCode.AI_REPORT_NOT_FOUND.getCode()
                || code == ErrorCode.ALBUM_NOT_FOUND.getCode()
                || code == ErrorCode.ALBUM_GROUP_NOT_FOUND.getCode()
                || code == ErrorCode.AI_ALBUM_PROPOSAL_NOT_FOUND.getCode()) {
            return HttpStatus.NOT_FOUND;
        }
        if (code == ErrorCode.DIARY_VERSION_CONFLICT.getCode()) return HttpStatus.CONFLICT;
        if (code == ErrorCode.DIARY_LOCKED.getCode()) return HttpStatus.LOCKED;
        if (code == ErrorCode.RATE_LIMITED.getCode()) return HttpStatus.TOO_MANY_REQUESTS;
        if (code == 500 || code == ErrorCode.INTERNAL_ERROR.getCode()) return HttpStatus.INTERNAL_SERVER_ERROR;
        return HttpStatus.BAD_REQUEST;
    }

    private String titleFor(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "需要登录";
            case FORBIDDEN -> "没有访问权限";
            case NOT_FOUND -> "资源不存在";
            case CONFLICT -> "数据版本冲突";
            case LOCKED -> "需要二次验证";
            case TOO_MANY_REQUESTS -> "请求过于频繁";
            case INTERNAL_SERVER_ERROR -> "服务器错误";
            default -> "请求无法处理";
        };
    }
}
