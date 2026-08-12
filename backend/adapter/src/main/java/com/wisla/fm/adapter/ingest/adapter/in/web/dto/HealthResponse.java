package com.wisla.fm.adapter.ingest.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthResponse(
        String status,
        String version,
        String database,
        String fm_module,
        Long buffered_count
) {
}
