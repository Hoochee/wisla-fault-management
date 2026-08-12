package ru.wisla.fm.ingestion.application.service;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.ingestion.application.port.in.IngestCommand;
import ru.wisla.fm.ingestion.domain.IngestOutcome;
import ru.wisla.fm.ingestion.domain.RawEvent;
import ru.wisla.fm.ingestion.domain.SourceIngestState;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Use-case test without a Spring context: the service is constructed directly with in-memory
 * outbound-port doubles.
 */
class IngestEventsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T10:15:30Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final UUID sourceId = UUID.randomUUID();
    private final InMemoryIngestionPorts.RawEventStore rawEventStore =
            new InMemoryIngestionPorts.RawEventStore();
    private final InMemoryIngestionPorts.EventSourceState eventSourceState =
            new InMemoryIngestionPorts.EventSourceState()
                    .with(new SourceIngestState(sourceId, "Demo source", "active"));
    private final InMemoryIngestionPorts.ProcessRawEventBatch processRawEventBatch =
            new InMemoryIngestionPorts.ProcessRawEventBatch();

    private final IngestEventsService service =
            new IngestEventsService(rawEventStore, eventSourceState, processRawEventBatch, FIXED_CLOCK);

    @Test
    void heartbeatAcknowledgesAndUpdatesSourceMetadataWithoutRawEvents() {
        IngestOutcome outcome = service.ingest(
                new IngestCommand(sourceId, true, List.of(), "1.2.3", Instant.parse("2026-08-03T10:00:00Z")));

        assertThat(outcome).isEqualTo(new IngestOutcome(0, 0, List.of(), true));
        assertThat(rawEventStore.savedEvents()).isEmpty();
        assertThat(processRawEventBatch.invocations()).isEmpty();
        assertThat(eventSourceState.marks())
                .containsExactly(new InMemoryIngestionPorts.EventSourceState.SuccessMark(sourceId, "1.2.3", NOW));
    }

    @Test
    void heartbeatWithoutAdapterVersionStillMarksSuccess() {
        service.ingest(new IngestCommand(sourceId, true, List.of(), null, null));

        assertThat(eventSourceState.marks())
                .containsExactly(new InMemoryIngestionPorts.EventSourceState.SuccessMark(sourceId, null, NOW));
    }

    @Test
    void eventBatchReportsAcceptedAndSharesOneIngestBatchId() {
        IngestOutcome outcome = service.ingest(new IngestCommand(
                sourceId,
                false,
                List.of(event("ext-1"), event("ext-2"), event("ext-3")),
                "1.0.0",
                NOW));

        assertThat(outcome.accepted()).isEqualTo(3);
        assertThat(outcome.rejected()).isZero();
        assertThat(outcome.rawEventIds()).hasSize(3).doesNotHaveDuplicates();
        assertThat(outcome.heartbeatAck()).isNull();

        Set<UUID> batchIds = rawEventStore.savedEvents().stream()
                .map(RawEvent::ingestBatchId)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(batchIds).hasSize(1);
        assertThat(batchIds.iterator().next()).isNotNull();

        assertThat(rawEventStore.savedEvents())
                .extracting(RawEvent::sourceId)
                .containsOnly(sourceId);
        assertThat(eventSourceState.marks())
                .containsExactly(new InMemoryIngestionPorts.EventSourceState.SuccessMark(sourceId, "1.0.0", NOW));
    }

    @Test
    void aSaveFailureIsCountedAsRejectedAndTheRestAreAccepted() {
        rawEventStore.failOn(rawEvent -> "ext-2".equals(rawEvent.externalId()));

        IngestOutcome outcome = service.ingest(new IngestCommand(
                sourceId,
                false,
                List.of(event("ext-1"), event("ext-2"), event("ext-3")),
                "1.0.0",
                NOW));

        assertThat(outcome.accepted()).isEqualTo(2);
        assertThat(outcome.rejected()).isEqualTo(1);
        assertThat(outcome.rawEventIds()).hasSize(2);
        assertThat(rawEventStore.savedEvents())
                .extracting(RawEvent::externalId)
                .containsExactly("ext-1", "ext-3");
        assertThat(processRawEventBatch.invocations()).containsExactly(outcome.rawEventIds());
    }

    @Test
    void unknownSourceThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> service.ingest(
                new IngestCommand(UUID.randomUUID(), false, List.of(event("ext-1")), "1.0.0", NOW)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source not found");

        assertThat(rawEventStore.savedEvents()).isEmpty();
        assertThat(eventSourceState.marks()).isEmpty();
    }

    @Test
    void processRawEventBatchIsInvokedOnlyForANonEmptyIdList() {
        service.ingest(new IngestCommand(sourceId, false, List.of(), "1.0.0", NOW));
        assertThat(processRawEventBatch.invocations()).isEmpty();

        rawEventStore.failOn(rawEvent -> true);
        service.ingest(new IngestCommand(sourceId, false, List.of(event("ext-1")), "1.0.0", NOW));
        assertThat(processRawEventBatch.invocations()).isEmpty();

        rawEventStore.failOn(rawEvent -> false);
        IngestOutcome outcome =
                service.ingest(new IngestCommand(sourceId, false, List.of(event("ext-9")), "1.0.0", NOW));
        assertThat(processRawEventBatch.invocations()).containsExactly(outcome.rawEventIds());
    }

    @Test
    void nullEventListIsTreatedAsAnEmptyBatch() {
        IngestOutcome outcome = service.ingest(new IngestCommand(sourceId, null, null, "1.0.0", NOW));

        assertThat(outcome).isEqualTo(new IngestOutcome(0, 0, List.of(), null));
        assertThat(processRawEventBatch.invocations()).isEmpty();
        assertThat(eventSourceState.marks()).hasSize(1);
    }

    @Test
    void eventWithoutExplicitStatusIsStoredAsNew() {
        service.ingest(new IngestCommand(
                sourceId,
                false,
                List.of(
                        new IngestCommand.IngestEvent(
                                "ext-default", "Title", null, "major", null, NOW, "host.example", null, null),
                        new IngestCommand.IngestEvent(
                                "ext-closed", "Title", null, "major", "closed", NOW, "host.example", null, null)),
                "1.0.0",
                NOW));

        assertThat(rawEventStore.savedEvents())
                .extracting(RawEvent::status)
                .containsExactly("new", "closed");
    }

    @Test
    void payloadFieldsAreCarriedOntoTheStoredRawEvent() {
        Instant occurredAt = Instant.parse("2026-08-03T09:00:00Z");
        service.ingest(new IngestCommand(
                sourceId,
                false,
                List.of(new IngestCommand.IngestEvent(
                        "ext-1",
                        "Disk full",
                        "Disk usage above threshold",
                        "critical",
                        "new",
                        occurredAt,
                        "demo-server.wisla.local",
                        Map.of("k", "v"),
                        Map.of("raw", true))),
                "1.0.0",
                NOW));

        RawEvent stored = rawEventStore.savedEvents().getFirst();
        assertThat(stored.externalId()).isEqualTo("ext-1");
        assertThat(stored.title()).isEqualTo("Disk full");
        assertThat(stored.description()).isEqualTo("Disk usage above threshold");
        assertThat(stored.severity()).isEqualTo("critical");
        assertThat(stored.nodeFqdn()).isEqualTo("demo-server.wisla.local");
        assertThat(stored.sourceAt()).isEqualTo(occurredAt);
        assertThat(stored.attributes()).containsEntry("k", "v");
        assertThat(stored.rawPayload()).containsEntry("raw", true);
        assertThat(stored.processed()).isFalse();
        assertThat(stored.processedEventId()).isNull();
    }

    @Test
    void missingPayloadMapsBecomeEmptyMaps() {
        service.ingest(new IngestCommand(sourceId, false, List.of(event("ext-1")), "1.0.0", NOW));

        RawEvent stored = rawEventStore.savedEvents().getFirst();
        assertThat(stored.attributes()).isEmpty();
        assertThat(stored.rawPayload()).isEmpty();
    }

    private static IngestCommand.IngestEvent event(String externalId) {
        return new IngestCommand.IngestEvent(
                externalId,
                "Title " + externalId,
                null,
                "major",
                "new",
                NOW,
                "host.example",
                null,
                null);
    }
}
