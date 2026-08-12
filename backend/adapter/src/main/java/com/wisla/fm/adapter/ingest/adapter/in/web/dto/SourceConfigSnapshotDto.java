package com.wisla.fm.adapter.ingest.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceConfigSnapshotDto(
        UUID source_id,
        String source_key,
        Map<String, Object> filter_rules,
        String api_key_hash,
        String endpoint,
        Boolean blocked,
        Instant ttl_expires_at,
        Instant updated_at
) {
}
