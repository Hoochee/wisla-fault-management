package ru.wisla.fm.rules.api;

import ru.wisla.fm.common.api.ErrorResponse;

import java.util.List;

public class CanvasValidationException extends RuntimeException {

    private final String code;
    private final List<ErrorResponse.FieldError> details;

    public CanvasValidationException(String message, String code, List<ErrorResponse.FieldError> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public List<ErrorResponse.FieldError> getDetails() {
        return details;
    }
}
