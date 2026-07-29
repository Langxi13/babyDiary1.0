package com.langxi.babydiary.v3.platform.application;

import org.springframework.http.HttpStatus;

public final class V3Exception extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public V3Exception(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static V3Exception badRequest(String code, String message) {
        return new V3Exception(HttpStatus.BAD_REQUEST, code, message);
    }

    public static V3Exception forbidden(String code, String message) {
        return new V3Exception(HttpStatus.FORBIDDEN, code, message);
    }

    public static V3Exception notFound(String code, String message) {
        return new V3Exception(HttpStatus.NOT_FOUND, code, message);
    }

    public static V3Exception conflict(String code, String message) {
        return new V3Exception(HttpStatus.CONFLICT, code, message);
    }
}
