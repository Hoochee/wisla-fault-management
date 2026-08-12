package ru.wisla.fm.processing.application.port.out;

import ru.wisla.fm.processing.domain.DedupKey;
import ru.wisla.fm.processing.domain.Event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code events} table as the processing use case needs it: the dedup lookup, the two threshold
 * queries and the correlation window, plus save and load.
 *
 * <p>Which derived query variant each of these resolves to is a persistence concern and stays in
 * {@code EventPersistenceAdapter} verbatim (design decision D4).
 */
public interface EventStorePort {

    Event save(Event event);

    Optional<Event> findById(UUID eventId);

    Optional<Event> findActiveDuplicate(DedupKey key);

    long countRecentBySeverity(UUID sourceId, UUID ciId, String severity, Instant since);

    boolean existsRecentByTitle(UUID sourceId, UUID ciId, String title, Instant since);

    List<Event> findWindow(Event processedEvent, String matchField, Instant since);
}
