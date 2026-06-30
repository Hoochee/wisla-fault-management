package com.wisla.fm.adapter.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProbeRequest(
        UUID source_id,
        String source_key,
        String ingest_api_key,
        Map<String, Object> test_payload
) {
}
