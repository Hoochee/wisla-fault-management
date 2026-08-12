package ru.wisla.fm.ingestion.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.wisla.fm.ingestion.application.port.in.IngestCommand;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public IngestCommand toCommand(UUID sourceId) {
        List<IngestCommand.IngestEvent> commandEvents = events == null
                ? List.of()
                : events.stream().map(IngestRequest::toCommandEvent).toList();
        return new IngestCommand(sourceId, heartbeat, commandEvents, adapterVersion, receivedAt);
    }

    private static IngestCommand.IngestEvent toCommandEvent(IngestEventPayload payload) {
        return new IngestCommand.IngestEvent(
                payload.externalId(),
                payload.title(),
                payload.description(),
                payload.severity(),
                payload.status(),
                payload.occurredAt(),
                payload.nodeFqdn(),
                payload.attributes(),
                payload.rawPayload());
    }
}
