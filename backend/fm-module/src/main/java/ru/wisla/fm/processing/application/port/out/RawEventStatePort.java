package ru.wisla.fm.processing.application.port.out;

import ru.wisla.fm.processing.domain.IncomingRawEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads the raw event being processed and writes back the outcome of processing it.
 *
 * <p>Both terminal writes carry the resolved {@code ciId}, because
 * {@code EventProcessingService.processRawEvent} bound it onto the managed raw-event row right after
 * the CI lookup — so a raw event that fails <em>after</em> its CI was resolved still has
 * {@code raw_events.ci_id} written alongside {@code processing_error}. A {@code null} {@code ciId}
 * leaves the column untouched.
 */
public interface RawEventStatePort {

    Optional<IncomingRawEvent> findById(UUID rawEventId);

    void markProcessed(UUID rawEventId, UUID eventId, UUID ciId);

    void recordError(UUID rawEventId, UUID ciId, String message);
}
