package ru.wisla.fm.ingestion.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wisla.fm.ingestion.application.port.in.QueryRawEventsUseCase;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/raw-events")
public class RawEventController {

    private final QueryRawEventsUseCase queryRawEvents;

    public RawEventController(QueryRawEventsUseCase queryRawEvents) {
        this.queryRawEvents = queryRawEvents;
    }

    @GetMapping
    public RawEventPage listRawEvents(
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean processed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return RawEventPage.from(queryRawEvents.query(sourceId, severity, processed, page, size));
    }
}
