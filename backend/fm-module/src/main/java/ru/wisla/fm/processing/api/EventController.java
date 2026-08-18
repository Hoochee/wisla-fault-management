package ru.wisla.fm.processing.api;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wisla.fm.common.api.NotFoundException;
import ru.wisla.fm.processing.application.port.in.EventActionCommand;
import ru.wisla.fm.processing.application.port.in.EventActionOutcome;
import ru.wisla.fm.processing.application.port.in.PerformEventActionUseCase;
import ru.wisla.fm.processing.domain.ActionLogEntry;
import ru.wisla.fm.processing.domain.EventNotFoundException;
import ru.wisla.fm.processing.domain.UserNotFoundException;
import ru.wisla.fm.processing.service.EventUpdateService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventQueryService eventQueryService;
    private final PerformEventActionUseCase performEventActionUseCase;
    private final EventUpdateService eventUpdateService;

    public EventController(EventQueryService eventQueryService,
                           PerformEventActionUseCase performEventActionUseCase,
                           EventUpdateService eventUpdateService) {
        this.eventQueryService = eventQueryService;
        this.performEventActionUseCase = performEventActionUseCase;
        this.eventUpdateService = eventUpdateService;
    }

    @GetMapping
    public EventPage listEvents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) UUID ciId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "false") boolean includeSilenced,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return eventQueryService.listEvents(
                status, severity, sourceId, ciId, productId, includeSilenced, sort, page, size);
    }

    @GetMapping("/{id}")
    public EventDetailDto getEvent(@PathVariable UUID id) {
        return eventQueryService.getEvent(id);
    }

    @PatchMapping("/{id}")
    public EventDto patchEvent(@PathVariable UUID id, @RequestBody EventPatch patch) {
        return eventUpdateService.patchEvent(id, patch);
    }

    @PostMapping("/{id}/actions")
    public EventActionResult postAction(
            @PathVariable UUID id,
            @Valid @RequestBody EventActionRequest request,
            Authentication authentication
    ) {
        UUID actorUserId = authentication != null ? (UUID) authentication.getPrincipal() : null;
        try {
            EventActionOutcome outcome = performEventActionUseCase.perform(new EventActionCommand(
                    id,
                    request.action(),
                    actorUserId,
                    request.comment(),
                    request.assignedUserId(),
                    request.silenceMinutes()));
            EventDto eventDto = eventQueryService.getEvent(id).event();
            ActionLogEntry log = outcome.log();
            return new EventActionResult(
                    eventDto,
                    new EventActionLogDto(
                            log.id(),
                            log.eventId(),
                            log.action(),
                            log.userName(),
                            log.userId(),
                            log.createdAt(),
                            log.details()));
        } catch (EventNotFoundException | UserNotFoundException ex) {
            throw new NotFoundException(ex.getMessage());
        }
    }
}
