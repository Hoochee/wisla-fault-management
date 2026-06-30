package ru.wisla.fm.ingestion.api;

import java.util.List;
import java.util.UUID;

public record IngestResponse(
        int accepted,
        int rejected,
        List<UUID> rawEventIds,
        Boolean heartbeatAck
) {
}
