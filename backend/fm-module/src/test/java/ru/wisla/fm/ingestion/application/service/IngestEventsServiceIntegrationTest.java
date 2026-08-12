package ru.wisla.fm.ingestion.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.wisla.fm.configuration.domain.EventSourceEntity;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.ingestion.adapter.in.web.IngestRequest;
import ru.wisla.fm.ingestion.adapter.out.persistence.RawEventJpaEntity;
import ru.wisla.fm.ingestion.adapter.out.persistence.RawEventJpaRepository;
import ru.wisla.fm.ingestion.application.port.in.IngestEventsUseCase;
import ru.wisla.fm.ingestion.domain.IngestOutcome;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ingest use case against the real adapters and H2, driven with a trusted {@code sourceId} the
 * way the Kafka path does.
 */
class IngestEventsServiceIntegrationTest extends AbstractFmModuleTest {

    @Autowired private IngestEventsUseCase ingestEvents;
    @Autowired private EventSourceRepository eventSourceRepository;
    @Autowired private RawEventJpaRepository rawEventRepository;

    @Test
    void ingestBySourceIdPersistsRawEvents() {
        EventSourceEntity source = eventSourceRepository.findByWebhookPathKey("demo").orElseThrow();
        long before = rawEventRepository.count();

        IngestRequest request = new IngestRequest(
                false,
                List.of(new IngestRequest.IngestEventPayload(
                        "svc-ext-" + System.nanoTime(),
                        "Service ingest",
                        "via sourceId",
                        "major",
                        "new",
                        Instant.now(),
                        "demo-server.wisla.local",
                        null,
                        null)),
                "1.0.0-facade",
                Instant.now());

        IngestOutcome outcome = ingestEvents.ingest(request.toCommand(source.getId()));

        assertThat(outcome.accepted()).isEqualTo(1);
        assertThat(outcome.rawEventIds()).hasSize(1);
        assertThat(rawEventRepository.count()).isEqualTo(before + 1);

        RawEventJpaEntity saved =
                rawEventRepository.findById(outcome.rawEventIds().getFirst()).orElseThrow();
        assertThat(saved.getSourceId()).isEqualTo(source.getId());
        assertThat(saved.getTitle()).isEqualTo("Service ingest");

        EventSourceEntity updated = eventSourceRepository.findById(source.getId()).orElseThrow();
        assertThat(updated.getAdapterVersion()).isEqualTo("1.0.0-facade");
        assertThat(updated.getLastSuccessAt()).isNotNull();
    }

    @Test
    void ingestHeartbeatBySourceIdUpdatesMetadataWithoutRawEvents() {
        EventSourceEntity source = eventSourceRepository.findByWebhookPathKey("demo").orElseThrow();
        long before = rawEventRepository.count();

        IngestRequest request = new IngestRequest(true, List.of(), "hb-facade", Instant.now());
        IngestOutcome outcome = ingestEvents.ingest(request.toCommand(source.getId()));

        assertThat(outcome.heartbeatAck()).isTrue();
        assertThat(outcome.accepted()).isZero();
        assertThat(rawEventRepository.count()).isEqualTo(before);

        EventSourceEntity updated = eventSourceRepository.findById(source.getId()).orElseThrow();
        assertThat(updated.getAdapterVersion()).isEqualTo("hb-facade");
        assertThat(updated.getLastSuccessAt()).isNotNull();
    }
}
