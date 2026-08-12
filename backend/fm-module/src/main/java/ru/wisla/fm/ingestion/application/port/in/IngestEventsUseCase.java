package ru.wisla.fm.ingestion.application.port.in;

import ru.wisla.fm.ingestion.domain.IngestOutcome;

public interface IngestEventsUseCase {

    /**
     * @throws IllegalArgumentException when the source is unknown
     */
    IngestOutcome ingest(IngestCommand command);
}
