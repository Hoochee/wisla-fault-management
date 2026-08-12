package ru.wisla.fm.ingestion.adapter.in.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.wisla.fm.ingestion.application.port.in.IngestEventsUseCase;
import ru.wisla.fm.ingestion.domain.IngestOutcome;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class IngestController {

    private final IngestEventsUseCase ingestEvents;

    public IngestController(IngestEventsUseCase ingestEvents) {
        this.ingestEvents = ingestEvents;
    }

    /**
     * Declares the ingest transaction: storing the raw events and processing the batch commit or
     * roll back together.
     */
    @PostMapping("/ingest")
    @Transactional
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request,
                                                 Authentication authentication) {
        UUID sourceId = (UUID) authentication.getPrincipal();
        IngestOutcome outcome = ingestEvents.ingest(request.toCommand(sourceId));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(IngestResponse.from(outcome));
    }
}
