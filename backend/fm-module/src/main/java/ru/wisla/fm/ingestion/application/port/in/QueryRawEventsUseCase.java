package ru.wisla.fm.ingestion.application.port.in;

import ru.wisla.fm.ingestion.domain.RawEventListing;

import java.util.UUID;

public interface QueryRawEventsUseCase {

    RawEventListing query(UUID sourceId, String severity, Boolean processed, int page, int size);
}
