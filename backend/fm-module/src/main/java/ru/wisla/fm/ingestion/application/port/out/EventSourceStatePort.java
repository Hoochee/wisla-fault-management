package ru.wisla.fm.ingestion.application.port.out;

import ru.wisla.fm.ingestion.domain.SourceIngestState;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EventSourceStatePort {

    Optional<SourceIngestState> find(UUID sourceId);

    /**
     * @param adapterVersion left untouched on the source when {@code null}
     */
    void markSuccess(UUID sourceId, String adapterVersion, Instant at);
}
