package ru.wisla.fm.ingestion.domain;

import java.util.UUID;

/**
 * The part of an event source the ingest slice needs: identity, display name and lifecycle status.
 */
public record SourceIngestState(
        UUID id,
        String name,
        String status
) {

    public boolean isActive() {
        return "active".equalsIgnoreCase(status);
    }
}
