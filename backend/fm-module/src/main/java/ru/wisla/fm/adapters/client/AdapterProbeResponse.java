package ru.wisla.fm.adapters.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdapterProbeResponse(
        boolean success,
        @JsonProperty("source_id") UUID sourceId,
        @JsonProperty("probed_at") Instant probedAt,
        String delivery,
        @JsonProperty("ingest_status") Integer ingestStatus,
        String error,
        @JsonProperty("latency_ms") Long latencyMs
) {
}
