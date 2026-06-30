package com.wisla.fm.adapter.web;

import com.wisla.fm.adapter.service.AdapterException;
import com.wisla.fm.adapter.web.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AdapterException.class)
    public ResponseEntity<ErrorResponse> handleAdapterException(AdapterException ex) {
        ErrorResponse body = new ErrorResponse(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }
}
