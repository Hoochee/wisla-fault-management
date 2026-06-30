package ru.wisla.fm.ingestion.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/raw-events")
public class RawEventController {

    private final RawEventQueryService rawEventQueryService;

    public RawEventController(RawEventQueryService rawEventQueryService) {
        this.rawEventQueryService = rawEventQueryService;
    }

    @GetMapping
    public RawEventPage listRawEvents(
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean processed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return rawEventQueryService.listRawEvents(sourceId, severity, processed, page, size);
    }
}
