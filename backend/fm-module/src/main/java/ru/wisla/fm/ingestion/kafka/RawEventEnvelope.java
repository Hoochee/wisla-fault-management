package ru.wisla.fm.ingestion.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.wisla.fm.ingestion.api.IngestRequest;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka value for topic {@code fm.raw-events}. Shape must stay aligned with the adapter producer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RawEventEnvelope(
        int schemaVersion,
        @NotNull UUID messageId,
        @NotNull Instant producedAt,
        @NotNull UUID sourceId,
        @NotBlank String sourceKey,
        @NotNull @Valid IngestRequest body
) {
}
