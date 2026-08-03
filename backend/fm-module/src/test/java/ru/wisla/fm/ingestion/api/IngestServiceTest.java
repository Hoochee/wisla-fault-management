package ru.wisla.fm.ingestion.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.wisla.fm.configuration.domain.EventSourceEntity;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.ingestion.domain.RawEventEntity;
import ru.wisla.fm.ingestion.persistence.RawEventRepository;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestServiceTest extends AbstractFmModuleTest {

    @Autowired private IngestService ingestService;
    @Autowired private EventSourceRepository eventSourceRepository;
    @Autowired private RawEventRepository rawEventRepository;

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

        IngestResponse response = ingestService.ingest(request, source.getId());

        assertThat(response.accepted()).isEqualTo(1);
        assertThat(response.rawEventIds()).hasSize(1);
        assertThat(rawEventRepository.count()).isEqualTo(before + 1);

        RawEventEntity saved = rawEventRepository.findById(response.rawEventIds().getFirst()).orElseThrow();
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
        IngestResponse response = ingestService.ingest(request, source.getId());

        assertThat(response.heartbeatAck()).isTrue();
        assertThat(response.accepted()).isZero();
        assertThat(rawEventRepository.count()).isEqualTo(before);

        EventSourceEntity updated = eventSourceRepository.findById(source.getId()).orElseThrow();
        assertThat(updated.getAdapterVersion()).isEqualTo("hb-facade");
        assertThat(updated.getLastSuccessAt()).isNotNull();
    }
}
