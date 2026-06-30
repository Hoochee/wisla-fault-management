package ru.wisla.fm.adapters.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdapterProbeRequest(
        @JsonProperty("source_id") UUID sourceId,
        @JsonProperty("ingest_api_key") String ingestApiKey,
        @JsonProperty("test_payload") Map<String, Object> testPayload
) {
}
