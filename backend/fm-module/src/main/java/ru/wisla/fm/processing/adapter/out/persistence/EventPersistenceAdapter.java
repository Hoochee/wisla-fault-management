package ru.wisla.fm.processing.adapter.out.persistence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.wisla.fm.processing.adapter.out.lifecycle.EventLifecyclePublisher;
import ru.wisla.fm.processing.application.port.out.EventStorePort;
import ru.wisla.fm.processing.domain.DedupKey;
import ru.wisla.fm.processing.domain.Event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code events} queries of the dedup, threshold and correlation paths, ported literally from
 * {@code DedupService}, {@code ThresholdService} and {@code CorrelationService} (design decision D4).
 * No query here is added, removed, merged or "corrected" — see
 * {@code DedupQueryCharacterizationTest}, {@code ThresholdQueryCharacterizationTest} and
 * {@code CorrelationQueryCharacterizationTest}.
 */
@Component
public class EventPersistenceAdapter implements EventStorePort {

    private static final List<String> ACTIVE_STATUSES = Event.ACTIVE_STATUSES;

    private final EventJpaRepository eventJpaRepository;
    private final EventJpaMapper eventJpaMapper;
    private final EventLifecyclePublisher lifecyclePublisher;

    public EventPersistenceAdapter(EventJpaRepository eventJpaRepository, EventJpaMapper eventJpaMapper) {
        this(eventJpaRepository, eventJpaMapper, null);
    }

    @Autowired
    public EventPersistenceAdapter(
            EventJpaRepository eventJpaRepository,
            EventJpaMapper eventJpaMapper,
            EventLifecyclePublisher lifecyclePublisher
    ) {
        this.eventJpaRepository = eventJpaRepository;
        this.eventJpaMapper = eventJpaMapper;
        this.lifecyclePublisher = lifecyclePublisher;
    }

    @Override
    public Event save(Event event) {
        boolean created = event.getId() == null;
        Event saved = eventJpaMapper.toDomain(eventJpaRepository.save(eventJpaMapper.toJpaEntity(event)));
        if (lifecyclePublisher != null) {
            lifecyclePublisher.afterSave(created, saved);
        }
        return saved;
    }

    @Override
    public Optional<Event> findById(UUID eventId) {
        return eventJpaRepository.findById(eventId).map(eventJpaMapper::toDomain);
    }

    /**
     * The three branches of {@code DedupService.findActiveDuplicate}, kept as they are. The middle
     * one looks redundant — it resolves to the same query as the last — but it is what documents that
     * {@code useCi = false} flips the CI predicate to {@code ci_id IS NULL} instead of dropping it.
     */
    @Override
    public Optional<Event> findActiveDuplicate(DedupKey key) {
        if (!key.lookupRequired()) {
            return Optional.empty();
        }
        if (key.ciId() != null) {
            return eventJpaRepository.findFirstBySourceIdAndTitleAndCiIdAndStatusIn(
                            key.sourceId(), key.title(), key.ciId(), ACTIVE_STATUSES)
                    .map(eventJpaMapper::toDomain);
        }
        if (key.ciId() == null && key.useCi()) {
            return eventJpaRepository.findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn(
                            key.sourceId(), key.title(), ACTIVE_STATUSES)
                    .map(eventJpaMapper::toDomain);
        }
        return eventJpaRepository.findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn(
                        key.sourceId(), key.title(), ACTIVE_STATUSES)
                .map(eventJpaMapper::toDomain);
    }

    @Override
    public long countRecentBySeverity(UUID sourceId, UUID ciId, String severity, Instant since) {
        if (ciId == null) {
            return eventJpaRepository.countBySourceIdAndCiIdIsNullAndSeverityAndCreatedAtAfterAndStatusIn(
                    sourceId, severity, since, ACTIVE_STATUSES
            );
        }
        return eventJpaRepository.countBySourceIdAndCiIdAndSeverityAndCreatedAtAfterAndStatusIn(
                sourceId, ciId, severity, since, ACTIVE_STATUSES
        );
    }

    @Override
    public boolean existsRecentByTitle(UUID sourceId, UUID ciId, String title, Instant since) {
        if (ciId == null) {
            return eventJpaRepository.existsBySourceIdAndCiIdIsNullAndTitleAndCreatedAtAfter(
                    sourceId, title, since
            );
        }
        return eventJpaRepository.existsBySourceIdAndCiIdAndTitleAndCreatedAtAfter(
                sourceId, ciId, title, since
        );
    }

    @Override
    public List<Event> findWindow(Event processedEvent, String matchField, Instant since) {
        UUID sourceId = processedEvent.getSourceId();
        UUID ciId = processedEvent.getCiId();
        return switch (matchField) {
            case "severity" -> findBySeverity(sourceId, ciId, processedEvent.getSeverity(), since);
            case "source" -> findBySource(sourceId, ciId, since);
            default -> findByTitle(sourceId, ciId, processedEvent.getTitle(), since);
        };
    }

    private List<Event> findByTitle(UUID sourceId, UUID ciId, String title, Instant since) {
        if (ciId == null) {
            return toDomain(eventJpaRepository
                    .findBySourceIdAndCiIdIsNullAndTitleAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                            sourceId, title, since, ACTIVE_STATUSES
                    ));
        }
        return toDomain(eventJpaRepository
                .findBySourceIdAndCiIdAndTitleAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                        sourceId, ciId, title, since, ACTIVE_STATUSES
                ));
    }

    private List<Event> findBySeverity(UUID sourceId, UUID ciId, String severity, Instant since) {
        if (ciId == null) {
            return toDomain(eventJpaRepository
                    .findBySourceIdAndCiIdIsNullAndSeverityAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                            sourceId, severity, since, ACTIVE_STATUSES
                    ));
        }
        return toDomain(eventJpaRepository
                .findBySourceIdAndCiIdAndSeverityAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                        sourceId, ciId, severity, since, ACTIVE_STATUSES
                ));
    }

    private List<Event> findBySource(UUID sourceId, UUID ciId, Instant since) {
        if (ciId == null) {
            return toDomain(eventJpaRepository
                    .findBySourceIdAndCiIdIsNullAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                            sourceId, since, ACTIVE_STATUSES
                    ));
        }
        return toDomain(eventJpaRepository
                .findBySourceIdAndCiIdAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                        sourceId, ciId, since, ACTIVE_STATUSES
                ));
    }

    private List<Event> toDomain(List<EventJpaEntity> entities) {
        return entities.stream().map(eventJpaMapper::toDomain).toList();
    }
}
