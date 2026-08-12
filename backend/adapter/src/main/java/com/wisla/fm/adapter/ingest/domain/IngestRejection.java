package com.wisla.fm.adapter.ingest.domain;

/**
 * Refusal of an ingest attempt, carrying the frozen error code and HTTP status that the web layer
 * renders as {@code ErrorResponse}.
 */
public class IngestRejection extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public IngestRejection(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
