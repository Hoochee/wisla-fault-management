package ru.wisla.fm.processing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.common.api.NotFoundException;
import ru.wisla.fm.identity.persistence.UserRepository;
import ru.wisla.fm.processing.api.EventActionLogDto;
import ru.wisla.fm.processing.api.EventActionRequest;
import ru.wisla.fm.processing.api.EventActionResult;
import ru.wisla.fm.processing.api.EventDto;
import ru.wisla.fm.processing.api.EventQueryService;
import ru.wisla.fm.processing.adapter.out.persistence.EventActionLogJpaEntity;
import ru.wisla.fm.processing.adapter.out.persistence.EventActionLogJpaRepository;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaEntity;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class EventActionService {

    private final EventJpaRepository eventRepository;
    private final EventActionLogJpaRepository eventActionLogRepository;
    private final UserRepository userRepository;
    private final EventQueryService eventQueryService;
    private final ObjectMapper objectMapper;

    public EventActionService(EventJpaRepository eventRepository,
                              EventActionLogJpaRepository eventActionLogRepository,
                              UserRepository userRepository,
                              EventQueryService eventQueryService,
                              ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.eventActionLogRepository = eventActionLogRepository;
        this.userRepository = userRepository;
        this.eventQueryService = eventQueryService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EventActionResult performAction(UUID eventId, EventActionRequest request, Authentication authentication) {
        EventJpaEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        UUID userId = authentication != null ? (UUID) authentication.getPrincipal() : null;
        String userName = resolveUserName(userId);

        String action = request.action();
        String details = buildDetails(action, request.comment(), userName);

        switch (action) {
            case "take" -> {
                if ("closed".equals(event.getStatus()) || "archived".equals(event.getStatus())) {
                    throw new IllegalStateException("Cannot take closed or archived event");
                }
                event.setStatus("in_progress");
                event.setAssignedUserId(userId);
                event.setTakenAt(Instant.now());
            }
            case "close" -> {
                if ("closed".equals(event.getStatus())) {
                    throw new IllegalStateException("Event is already closed");
                }
                event.setStatus("closed");
                event.setClosedAt(Instant.now());
            }
            case "comment" -> {
                if (request.comment() == null || request.comment().isBlank()) {
                    throw new IllegalArgumentException("Comment is required for comment action");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported action: " + action);
        }

        EventJpaEntity saved = eventRepository.save(event);
        EventActionLogJpaEntity log = new EventActionLogJpaEntity();
        log.setEventId(saved.getId());
        log.setAction(action);
        log.setUserId(userId);
        log.setUserName(userName);
        log.setDetails(details);
        if (request.comment() != null && !request.comment().isBlank()) {
            log.setMetadata(toMetadata(request.comment()));
        }
        EventActionLogJpaEntity savedLog = eventActionLogRepository.save(log);

        EventDto eventDto = eventQueryService.toDto(saved);
        return new EventActionResult(eventDto, toLogDto(savedLog));
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return "system";
        }
        return userRepository.findById(userId)
                .map(user -> user.getFullName())
                .orElse("unknown");
    }

    private String buildDetails(String action, String comment, String userName) {
        return switch (action) {
            case "take" -> userName + " took the event";
            case "close" -> userName + " closed the event";
            case "comment" -> userName + " commented: " + comment;
            default -> userName + " performed " + action;
        };
    }

    private String toMetadata(String comment) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("comment", comment));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private EventActionLogDto toLogDto(EventActionLogJpaEntity log) {
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
}
