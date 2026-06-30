package ru.wisla.fm.processing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.processing.canvas.CorrelationConfig;
import ru.wisla.fm.processing.domain.EventEntity;
import ru.wisla.fm.processing.persistence.EventRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class CorrelationService {

    private static final List<String> ACTIVE_STATUSES = List.of("new", "in_progress", "maintenance", "deferred");

    private final EventRepository eventRepository;

    public CorrelationService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public void evaluateAfterProcessing(EventEntity processedEvent, CorrelationConfig config) {
        Instant since = Instant.now().minus(config.windowMin(), ChronoUnit.MINUTES);
        List<EventEntity> windowEvents = findWindowEvents(processedEvent, config, since);
        if (windowEvents.size() < config.count()) {
            return;
        }
        EventEntity root = windowEvents.getFirst();
        if (root.getRootEventId() != null) {
            root = eventRepository.findById(root.getRootEventId()).orElse(root);
        }
        if (processedEvent.getId().equals(root.getId())) {
            return;
        }
        processedEvent.setRootEventId(root.getId());
        eventRepository.save(processedEvent);
    }

    private List<EventEntity> findWindowEvents(EventEntity processedEvent, CorrelationConfig config, Instant since) {
        UUID sourceId = processedEvent.getSourceId();
        UUID ciId = processedEvent.getCiId();
        return switch (config.matchField()) {
            case "severity" -> findBySeverity(sourceId, ciId, processedEvent.getSeverity(), since);
            case "source" -> findBySource(sourceId, ciId, since);
            default -> findByTitle(sourceId, ciId, processedEvent.getTitle(), since);
        };
    }

    private List<EventEntity> findByTitle(UUID sourceId, UUID ciId, String title, Instant since) {
        if (ciId == null) {
            return eventRepository.findBySourceIdAndCiIdIsNullAndTitleAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                    sourceId, title, since, ACTIVE_STATUSES
            );
        }
        return eventRepository.findBySourceIdAndCiIdAndTitleAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                sourceId, ciId, title, since, ACTIVE_STATUSES
        );
    }

    private List<EventEntity> findBySeverity(UUID sourceId, UUID ciId, String severity, Instant since) {
        if (ciId == null) {
            return eventRepository.findBySourceIdAndCiIdIsNullAndSeverityAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                    sourceId, severity, since, ACTIVE_STATUSES
            );
        }
        return eventRepository.findBySourceIdAndCiIdAndSeverityAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                sourceId, ciId, severity, since, ACTIVE_STATUSES
        );
    }

    private List<EventEntity> findBySource(UUID sourceId, UUID ciId, Instant since) {
        if (ciId == null) {
            return eventRepository.findBySourceIdAndCiIdIsNullAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                    sourceId, since, ACTIVE_STATUSES
            );
        }
        return eventRepository.findBySourceIdAndCiIdAndCreatedAtAfterAndStatusInOrderByCreatedAtAsc(
                sourceId, ciId, since, ACTIVE_STATUSES
        );
    }
}
