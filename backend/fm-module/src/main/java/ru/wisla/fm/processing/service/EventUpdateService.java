package ru.wisla.fm.processing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.common.api.NotFoundException;
import ru.wisla.fm.processing.api.EventDto;
import ru.wisla.fm.processing.api.EventPatch;
import ru.wisla.fm.processing.api.EventQueryService;
import ru.wisla.fm.processing.adapter.out.lifecycle.EventLifecyclePublisher;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaEntity;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EventUpdateService {

    private final EventJpaRepository eventRepository;
    private final EventQueryService eventQueryService;
    private final ObjectMapper objectMapper;
    private final EventLifecyclePublisher lifecyclePublisher;

    public EventUpdateService(EventJpaRepository eventRepository,
                              EventQueryService eventQueryService,
                              ObjectMapper objectMapper,
                              EventLifecyclePublisher lifecyclePublisher) {
        this.eventRepository = eventRepository;
        this.eventQueryService = eventQueryService;
        this.objectMapper = objectMapper;
        this.lifecyclePublisher = lifecyclePublisher;
    }

    @Transactional
    public EventDto patchEvent(UUID id, EventPatch patch) {
        EventJpaEntity event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (patch.status() != null) {
            validateStatusTransition(event.getStatus(), patch.status());
            event.setStatus(patch.status());
            if ("closed".equals(patch.status())) {
                event.setClosedAt(Instant.now());
            }
        }
        if (patch.severity() != null) {
            event.setSeverity(patch.severity());
        }
        if (patch.assignedUserId() != null) {
            event.setAssignedUserId(patch.assignedUserId());
        }
        if (patch.title() != null) {
            event.setTitle(patch.title());
        }
        if (patch.description() != null) {
            event.setDescription(patch.description());
        }
        if (patch.tags() != null) {
            event.setTags(toJson(patch.tags()));
        }
        if (patch.itsmIncidentNumber() != null) {
            event.setItsmIncidentNumber(patch.itsmIncidentNumber());
        }

        EventJpaEntity saved = eventRepository.save(event);
        lifecyclePublisher.afterStatusSave(saved.getStatus(), saved.getId(), saved.getCiId());
        return eventQueryService.toDto(saved);
    }

    private void validateStatusTransition(String current, String next) {
        if ("archived".equals(current) && !"archived".equals(next)) {
            throw new IllegalStateException("Cannot change status of archived event");
        }
    }

    private String toJson(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
