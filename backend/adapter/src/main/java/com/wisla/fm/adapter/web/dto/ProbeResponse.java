package com.wisla.fm.adapter.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProbeResponse(
        boolean success,
        UUID source_id,
        Instant probed_at,
        String delivery,
        Integer ingest_status,
        String error,
        Long latency_ms
) {
}
