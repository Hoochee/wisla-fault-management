package ru.wisla.fm.processing.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.processing.canvas.DedupConfig;
import ru.wisla.fm.processing.domain.EventEntity;
import ru.wisla.fm.processing.persistence.EventRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DedupService {

    private static final List<String> ACTIVE_STATUSES = List.of("new", "in_progress", "maintenance", "deferred");

    private final EventRepository eventRepository;

    public DedupService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventEntity mergeOrCreate(EventEntity candidate) {
        return mergeOrCreate(candidate, DedupConfig.defaults());
    }

    @Transactional
    public EventEntity mergeOrCreate(EventEntity candidate, DedupConfig config) {
        Optional<EventEntity> existing = findActiveDuplicate(candidate, config);
        if (existing.isPresent()) {
            EventEntity event = existing.get();
            event.setRepeatCount(event.getRepeatCount() + 1);
            event.setLastRepeatAt(Instant.now());
            if (isMoreSevere(candidate.getSeverity(), event.getSeverity())) {
                event.setSeverity(candidate.getSeverity());
            }
            return eventRepository.save(event);
        }
        return eventRepository.save(candidate);
    }

    private Optional<EventEntity> findActiveDuplicate(EventEntity candidate, DedupConfig config) {
        UUID sourceId = config.useSource() ? candidate.getSourceId() : null;
        String title = config.useTitle() ? candidate.getTitle() : null;
        UUID ciId = config.useCi() ? candidate.getCiId() : null;

        if (sourceId == null && title == null && ciId == null) {
            return Optional.empty();
        }

        if (ciId != null) {
            return eventRepository.findFirstBySourceIdAndTitleAndCiIdAndStatusIn(
                    sourceId != null ? sourceId : candidate.getSourceId(),
                    title != null ? title : candidate.getTitle(),
                    ciId,
                    ACTIVE_STATUSES
            );
        }
        if (ciId == null && config.useCi()) {
            return eventRepository.findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn(
                    sourceId != null ? sourceId : candidate.getSourceId(),
                    title != null ? title : candidate.getTitle(),
                    ACTIVE_STATUSES
            );
        }
        return eventRepository.findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn(
                sourceId != null ? sourceId : candidate.getSourceId(),
                title != null ? title : candidate.getTitle(),
                ACTIVE_STATUSES
        );
    }

    private boolean isMoreSevere(String incoming, String current) {
        return severityRank(incoming) < severityRank(current);
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "fatal" -> 0;
            case "critical" -> 1;
            case "major" -> 2;
            case "minor" -> 3;
            case "warning" -> 4;
            default -> 5;
        };
    }
}
