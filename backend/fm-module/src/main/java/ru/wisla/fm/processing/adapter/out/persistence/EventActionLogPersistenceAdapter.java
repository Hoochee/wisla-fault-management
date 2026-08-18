package ru.wisla.fm.processing.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.wisla.fm.processing.application.port.out.EventActionLogPort;
import ru.wisla.fm.processing.domain.ActionLogEntry;

import java.util.Map;

@Component
public class EventActionLogPersistenceAdapter implements EventActionLogPort {

    private final EventActionLogJpaRepository eventActionLogRepository;
    private final ObjectMapper objectMapper;

    public EventActionLogPersistenceAdapter(EventActionLogJpaRepository eventActionLogRepository,
                                            ObjectMapper objectMapper) {
        this.eventActionLogRepository = eventActionLogRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ActionLogEntry append(ActionLogEntry entry) {
        EventActionLogJpaEntity entity = new EventActionLogJpaEntity();
        entity.setEventId(entry.eventId());
        entity.setAction(entry.action());
        entity.setUserId(entry.userId());
        entity.setUserName(entry.userName());
        entity.setDetails(entry.details());
        if (entry.comment() != null && !entry.comment().isBlank()) {
            entity.setMetadata(toMetadata(entry.comment()));
        }
        EventActionLogJpaEntity saved = eventActionLogRepository.save(entity);
        return entry.withPersisted(saved.getId(), saved.getCreatedAt());
    }

    private String toMetadata(String comment) {
        try {
            return objectMapper.writeValueAsString(Map.of("comment", comment));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
