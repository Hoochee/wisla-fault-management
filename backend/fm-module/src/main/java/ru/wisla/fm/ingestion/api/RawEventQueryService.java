package ru.wisla.fm.ingestion.api;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import ru.wisla.fm.common.api.PageMeta;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.ingestion.domain.RawEventEntity;
import ru.wisla.fm.ingestion.persistence.RawEventRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RawEventQueryService {

    private final RawEventRepository rawEventRepository;
    private final EventSourceRepository eventSourceRepository;

    public RawEventQueryService(RawEventRepository rawEventRepository,
                                EventSourceRepository eventSourceRepository) {
        this.rawEventRepository = rawEventRepository;
        this.eventSourceRepository = eventSourceRepository;
    }

    public RawEventPage listRawEvents(UUID sourceId, String severity, Boolean processed, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 500);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<RawEventEntity> spec = buildSpec(sourceId, severity, processed);
        Page<RawEventEntity> result = rawEventRepository.findAll(spec, pageable);
        return new RawEventPage(
                result.getContent().stream().map(this::toDto).toList(),
                PageMeta.of(result.getNumber(), result.getSize(), result.getTotalElements())
        );
    }

    private RawEventDto toDto(RawEventEntity raw) {
        String sourceName = eventSourceRepository.findById(raw.getSourceId())
                .map(source -> source.getName())
                .orElse(null);
        return new RawEventDto(
                raw.getId(),
                raw.getStatus(),
                raw.getSeverity(),
                raw.getTitle(),
                raw.getDescription(),
                null,
                null,
                1,
                raw.getCiId(),
                raw.getNodeFqdn(),
                null,
                null,
                raw.getSourceId(),
                sourceName,
                null,
                List.of(),
                null,
                List.of(),
                raw.getCreatedAt(),
                raw.getSourceAt(),
                null,
                null,
                null,
                raw.getUpdatedAt(),
                true,
                raw.isProcessed(),
                raw.getProcessedEventId(),
                raw.getIngestBatchId()
        );
    }

    private Specification<RawEventEntity> buildSpec(UUID sourceId, String severity, Boolean processed) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (sourceId != null) {
                predicates.add(cb.equal(root.get("sourceId"), sourceId));
            }
            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (processed != null) {
                predicates.add(cb.equal(root.get("processed"), processed));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
