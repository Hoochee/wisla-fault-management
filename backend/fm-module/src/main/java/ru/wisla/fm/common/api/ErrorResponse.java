package ru.wisla.fm.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String message,
        List<FieldError> details
) {
    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, null);
    }
}
