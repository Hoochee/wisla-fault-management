package ru.wisla.fm.processing.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * The only entry point of the processing context: turn a batch of freshly ingested raw events into
 * events, applying the enabled rules. Called by {@code ingestion}'s {@code ProcessRawEventBatchPort}
 * inside the ingest transaction.
 */
public interface ProcessRawEventBatchUseCase {

    void process(List<UUID> rawEventIds);
}
