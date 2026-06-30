package com.wisla.fm.adapter.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String message,
        Map<String, Object> details
) {
    public ErrorResponse(String error, String message) {
        this(error, message, null);
    }
}
