package ru.wisla.fm.ingestion.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IngestRequest(
        Boolean heartbeat,
        List<@Valid IngestEventPayload> events,
        String adapterVersion,
        Instant receivedAt
) {
    public record IngestEventPayload(
            @NotBlank String externalId,
            @NotBlank String title,
            String description,
            @NotBlank String severity,
            String status,
            @NotNull Instant occurredAt,
            String nodeFqdn,
            Map<String, Object> attributes,
            Map<String, Object> rawPayload
    ) {
    }
}
