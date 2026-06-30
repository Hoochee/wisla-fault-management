package ru.wisla.fm.common.api;

import org.springframework.http.HttpStatus;

public class UpstreamException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public UpstreamException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
