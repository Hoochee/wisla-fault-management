package ru.wisla.fm.processing.api;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import ru.wisla.fm.common.api.NotFoundException;
import ru.wisla.fm.common.api.PageMeta;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.identity.persistence.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.wisla.fm.processing.domain.EventActionLogEntity;
import ru.wisla.fm.processing.domain.EventEntity;
import ru.wisla.fm.processing.persistence.EventActionLogRepository;
import ru.wisla.fm.processing.persistence.EventRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class EventQueryService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "createdAt", "lastRepeatAt", "repeatCount", "severity", "status", "title", "nodeFqdn", "systemName"
    );

    private final EventRepository eventRepository;
    private final EventActionLogRepository eventActionLogRepository;
    private final EventSourceRepository eventSourceRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public EventQueryService(EventRepository eventRepository,
                             EventActionLogRepository eventActionLogRepository,
                             EventSourceRepository eventSourceRepository,
                             UserRepository userRepository,
                             ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.eventActionLogRepository = eventActionLogRepository;
        this.eventSourceRepository = eventSourceRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public EventPage listEvents(String status, String severity, UUID sourceId, UUID ciId, String sort, int page, int size) {
        ParsedSort parsedSort = parseSort(sort);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 500);

        Specification<EventEntity> spec = buildSpec(status, severity, sourceId, ciId);
        PageRequest pageable;
        if ("severity".equals(parsedSort.field())) {
            spec = spec.and(severitySortSpec(parsedSort.ascending()));
            pageable = PageRequest.of(safePage, safeSize);
        } else if ("lastRepeatAt".equals(parsedSort.field())) {
            spec = spec.and(lastRepeatAtSortSpec(parsedSort.ascending()));
            pageable = PageRequest.of(safePage, safeSize);
        } else {
            pageable = PageRequest.of(safePage, safeSize, buildSort(parsedSort));
        }

        Page<EventEntity> result = eventRepository.findAll(spec, pageable);
        return new EventPage(
                result.getContent().stream().map(this::toDto).toList(),
                PageMeta.of(result.getNumber(), result.getSize(), result.getTotalElements())
        );
    }

    public EventDetailDto getEvent(UUID id) {
        EventEntity event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        List<EventActionLogDto> logs = eventActionLogRepository.findByEventIdOrderByCreatedAtDesc(id).stream()
                .map(this::toLogDto)
                .toList();
        List<UUID> childIds = eventRepository.findByRootEventId(id).stream()
                .map(EventEntity::getId)
                .toList();
        return new EventDetailDto(toDto(event, childIds), logs, event.getRawEventId());
    }

    public EventDto toDto(EventEntity event) {
        return toDto(event, List.of());
    }

    public EventDto toDto(EventEntity event, List<UUID> childEventIds) {
        String sourceName = eventSourceRepository.findById(event.getSourceId())
                .map(source -> source.getName())
                .orElse(null);
        String assignedUserName = event.getAssignedUserId() == null
                ? null
                : userRepository.findById(event.getAssignedUserId())
                        .map(user -> user.getFullName())
                        .orElse(null);
        return new EventDto(
                event.getId(),
                event.getStatus(),
                event.getSeverity(),
                event.getTitle(),
                event.getDescription(),
                event.getAssignedUserId(),
                assignedUserName,
                event.getRepeatCount(),
                event.getCiId(),
                event.getNodeFqdn(),
                event.getSystemName(),
                event.getSubsystemName(),
                event.getSourceId(),
                sourceName,
                event.getRootEventId(),
                childEventIds,
                event.getItsmIncidentNumber(),
                parseTags(event.getTags()),
                event.getCreatedAt(),
                event.getSourceAt(),
                event.getLastRepeatAt(),
                event.getTakenAt(),
                event.getClosedAt(),
                event.getUpdatedAt()
        );
    }

    private EventActionLogDto toLogDto(EventActionLogEntity log) {
        return new EventActionLogDto(
                log.getId(),
                log.getEventId(),
                log.getAction(),
                log.getUserName(),
                log.getUserId(),
                log.getCreatedAt(),
                log.getDetails()
        );
    }

    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Specification<EventEntity> buildSpec(String status, String severity, UUID sourceId, UUID ciId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (sourceId != null) {
                predicates.add(cb.equal(root.get("sourceId"), sourceId));
            }
            if (ciId != null) {
                predicates.add(cb.equal(root.get("ciId"), ciId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private ParsedSort parseSort(String sort) {
        String effective = (sort == null || sort.isBlank()) ? "createdAt,desc" : sort.trim();
        String[] parts = effective.split(",", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid sort parameter: " + sort);
        }
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Unknown sort field: " + field);
        }
        String direction = parts[1].trim().toLowerCase(Locale.ROOT);
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new IllegalArgumentException("Invalid sort direction: " + parts[1].trim());
        }
        return new ParsedSort(field, "asc".equals(direction));
    }

    private Sort buildSort(ParsedSort parsedSort) {
        Sort.Direction direction = parsedSort.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, parsedSort.field());
    }

    private Specification<EventEntity> lastRepeatAtSortSpec(boolean ascending) {
        return (root, query, cb) -> {
            if (!Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                Expression<Integer> nullRank = cb.<Integer>selectCase()
                        .when(cb.isNull(root.get("lastRepeatAt")), ascending ? 0 : 1)
                        .otherwise(ascending ? 1 : 0);
                Order valueOrder = ascending
                        ? cb.asc(root.get("lastRepeatAt"))
                        : cb.desc(root.get("lastRepeatAt"));
                query.orderBy(cb.asc(nullRank), valueOrder);
            }
            return cb.conjunction();
        };
    }

    private Specification<EventEntity> severitySortSpec(boolean ascending) {
        return (root, query, cb) -> {
            if (!Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                Expression<Integer> rank = cb.<Integer>selectCase()
                        .when(cb.equal(root.get("severity"), "fatal"), 0)
                        .when(cb.equal(root.get("severity"), "critical"), 1)
                        .when(cb.equal(root.get("severity"), "major"), 2)
                        .when(cb.equal(root.get("severity"), "minor"), 3)
                        .when(cb.equal(root.get("severity"), "warning"), 4)
                        .otherwise(5);
                Order order = ascending ? cb.asc(rank) : cb.desc(rank);
                query.orderBy(order);
            }
            return cb.conjunction();
        };
    }

    private record ParsedSort(String field, boolean ascending) {}
}
