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
import ru.wisla.fm.processing.service.EventActionService;
import ru.wisla.fm.processing.service.EventUpdateService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventQueryService eventQueryService;
    private final EventActionService eventActionService;
    private final EventUpdateService eventUpdateService;

    public EventController(EventQueryService eventQueryService,
                           EventActionService eventActionService,
                           EventUpdateService eventUpdateService) {
        this.eventQueryService = eventQueryService;
        this.eventActionService = eventActionService;
        this.eventUpdateService = eventUpdateService;
    }

    @GetMapping
    public EventPage listEvents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) UUID ciId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return eventQueryService.listEvents(status, severity, sourceId, ciId, sort, page, size);
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
        return eventActionService.performAction(id, request, authentication);
    }
}
