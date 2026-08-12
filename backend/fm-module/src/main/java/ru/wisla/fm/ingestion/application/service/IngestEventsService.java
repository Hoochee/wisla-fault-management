package ru.wisla.fm.ingestion.application.service;

import ru.wisla.fm.ingestion.application.port.in.IngestCommand;
import ru.wisla.fm.ingestion.application.port.in.IngestEventsUseCase;
import ru.wisla.fm.ingestion.application.port.out.EventSourceStatePort;
import ru.wisla.fm.ingestion.application.port.out.ProcessRawEventBatchPort;
import ru.wisla.fm.ingestion.application.port.out.RawEventStorePort;
import ru.wisla.fm.ingestion.domain.IngestOutcome;
import ru.wisla.fm.ingestion.domain.RawEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared ingest entry for HTTP (after API-key auth) and the Kafka consumer (trusted
 * {@code sourceId}). The transaction is declared by the inbound adapter, so ingest and the
 * subsequent batch processing run as one unit of work.
 */
public class IngestEventsService implements IngestEventsUseCase {

    private final RawEventStorePort rawEventStore;
    private final EventSourceStatePort eventSourceState;
    private final ProcessRawEventBatchPort processRawEventBatch;
    private final Clock clock;

    public IngestEventsService(RawEventStorePort rawEventStore,
                              EventSourceStatePort eventSourceState,
                              ProcessRawEventBatchPort processRawEventBatch,
                              Clock clock) {
        this.rawEventStore = rawEventStore;
        this.eventSourceState = eventSourceState;
        this.processRawEventBatch = processRawEventBatch;
        this.clock = clock;
    }

    @Override
    public IngestOutcome ingest(IngestCommand command) {
        eventSourceState.find(command.sourceId())
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));

        if (command.isHeartbeat()) {
            markSuccess(command);
            return IngestOutcome.heartbeatAcknowledged();
        }

        UUID batchId = UUID.randomUUID();
        List<UUID> rawEventIds = new ArrayList<>();
        int accepted = 0;
        int rejected = 0;

        for (IngestCommand.IngestEvent event : command.eventsOrEmpty()) {
            try {
                rawEventIds.add(rawEventStore.save(toRawEvent(event, command.sourceId(), batchId)));
                accepted++;
            } catch (Exception e) {
                rejected++;
            }
        }

        markSuccess(command);

        if (!rawEventIds.isEmpty()) {
            processRawEventBatch.process(rawEventIds);
        }

        return new IngestOutcome(accepted, rejected, rawEventIds, null);
    }

    private void markSuccess(IngestCommand command) {
        eventSourceState.markSuccess(command.sourceId(), command.adapterVersion(), Instant.now(clock));
    }

    private static RawEvent toRawEvent(IngestCommand.IngestEvent event, UUID sourceId, UUID batchId) {
        return RawEvent.incoming(
                sourceId,
                event.externalId(),
                event.title(),
                event.description(),
                event.severity(),
                event.status(),
                event.nodeFqdn(),
                event.occurredAt(),
                event.attributes(),
                event.rawPayload(),
                batchId);
    }
}
