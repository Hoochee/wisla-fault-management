package ru.wisla.fm.ingestion.adapter.in.web;

import ru.wisla.fm.ingestion.domain.IngestOutcome;

import java.util.List;
import java.util.UUID;

public record IngestResponse(
        int accepted,
        int rejected,
        List<UUID> rawEventIds,
        Boolean heartbeatAck
) {

    public static IngestResponse from(IngestOutcome outcome) {
        return new IngestResponse(
                outcome.accepted(),
                outcome.rejected(),
                outcome.rawEventIds(),
                outcome.heartbeatAck());
    }
}
