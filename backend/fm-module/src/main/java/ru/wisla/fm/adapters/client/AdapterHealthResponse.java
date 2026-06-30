package ru.wisla.fm.adapters.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdapterHealthResponse(
        String status,
        String version,
        String database,
        @JsonProperty("fm_module") String fmModule,
        @JsonProperty("buffered_count") Long bufferedCount
) {
}
