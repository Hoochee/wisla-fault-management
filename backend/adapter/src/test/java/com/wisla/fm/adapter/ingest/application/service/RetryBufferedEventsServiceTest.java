package com.wisla.fm.adapter.ingest.application.service;

import com.wisla.fm.adapter.ingest.application.port.out.RawEventPublisherPort;
import com.wisla.fm.adapter.ingest.domain.BufferedEvent;
import com.wisla.fm.adapter.ingest.domain.FilterRules;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;
import com.wisla.fm.adapter.ingest.testsupport.InMemoryBufferedEventStore;
import com.wisla.fm.adapter.ingest.testsupport.InMemorySourceConfigStore;
import com.wisla.fm.adapter.ingest.testsupport.RecordingRawEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RetryBufferedEventsServiceTest {

    private static final UUID SOURCE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String SOURCE_KEY = "buffer-retry-source";
    private static final String ADAPTER_VERSION = "1.2.3";
    private static final int BUFFER_RETRY_BASE_SECONDS = 30;
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemorySourceConfigStore sourceConfigs = new InMemorySourceConfigStore();
    private final InMemoryBufferedEventStore bufferedEvents = new InMemoryBufferedEventStore();
    private final RecordingRawEventPublisher publisher = new RecordingRawEventPublisher();

    private final RetryBufferedEventsService service = new RetryBufferedEventsService(
            sourceConfigs,
            bufferedEvents,
            publisher,
            CLOCK,
            ADAPTER_VERSION,
            BUFFER_RETRY_BASE_SECONDS
    );

    @Test
    void successfulRepublishDeletesTheBufferedEvent() {
        sourceConfigs.put(config(NOW.plusSeconds(3600)));
        BufferedEvent due = due();
        bufferedEvents.put(due);

        service.retryDueMessages(NOW);

        assertThat(bufferedEvents.count()).isZero();
        assertThat(bufferedEvents.deletedIds()).containsExactly(due.id());
        assertThat(publisher.publishCount()).isEqualTo(1);
        assertThat(publisher.last().sourceId()).isEqualTo(SOURCE_ID);
        assertThat(publisher.last().sourceKey()).isEqualTo(SOURCE_KEY);
        assertThat(publisher.last().ingestBody()).containsEntry("adapterVersion", ADAPTER_VERSION);
    }

    @Test
    void permanentFailureAlsoDeletesTheBufferedEvent() {
        sourceConfigs.put(config(NOW.plusSeconds(3600)));
        BufferedEvent due = due();
        bufferedEvents.put(due);
        publisher.stub(RawEventPublisherPort.PublishResult.permanent("unrecoverable"));

        service.retryDueMessages(NOW);

        assertThat(bufferedEvents.count()).isZero();
        assertThat(bufferedEvents.deletedIds()).containsExactly(due.id());
    }

    @Test
    void retryableFailureReschedulesWithExponentialBackoff() {
        sourceConfigs.put(config(NOW.plusSeconds(3600)));
        BufferedEvent due = due();
        bufferedEvents.put(due);
        publisher.stub(RawEventPublisherPort.PublishResult.retryable("broker down"));

        service.retryDueMessages(NOW);

        assertThat(bufferedEvents.deletedIds()).isEmpty();
        assertThat(bufferedEvents.all()).hasSize(1);
        BufferedEvent rescheduled = bufferedEvents.all().getFirst();
        assertThat(rescheduled.retryCount()).isEqualTo(1);
        assertThat(rescheduled.nextRetryAt()).isEqualTo(NOW.plusSeconds(BUFFER_RETRY_BASE_SECONDS));
        assertThat(rescheduled.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void missingSourceConfigReschedulesWithoutPublishing() {
        BufferedEvent due = due();
        bufferedEvents.put(due);

        service.retryDueMessages(NOW);

        assertThat(publisher.publishCount()).isZero();
        assertThat(bufferedEvents.deletedIds()).isEmpty();
        assertThat(bufferedEvents.all().getFirst().retryCount()).isEqualTo(1);
    }

    /** The retry path deliberately does not apply the snapshot TTL, matching the current worker. */
    @Test
    void expiredSourceConfigIsStillUsedForTheRetry() {
        sourceConfigs.put(config(NOW.minusSeconds(1)));
        bufferedEvents.put(due());

        service.retryDueMessages(NOW);

        assertThat(publisher.publishCount()).isEqualTo(1);
        assertThat(bufferedEvents.count()).isZero();
    }

    @Test
    void onlyDueEventsAreRetried() {
        sourceConfigs.put(config(NOW.plusSeconds(3600)));
        bufferedEvents.put(due());
        bufferedEvents.put(BufferedEvent.create(
                SOURCE_ID, "api-key", Map.of("event_id", "later"), NOW.plusSeconds(60), NOW));

        service.retryDueMessages(NOW);

        assertThat(publisher.publishCount()).isEqualTo(1);
        assertThat(bufferedEvents.count()).isEqualTo(1);
    }

    @Test
    void everyDueEventIsRetriedInOneRun() {
        sourceConfigs.put(config(NOW.plusSeconds(3600)));
        bufferedEvents.put(due());
        bufferedEvents.put(due());
        bufferedEvents.put(due());

        service.retryDueMessages(NOW);

        assertThat(publisher.publishCount()).isEqualTo(3);
        assertThat(bufferedEvents.count()).isZero();
    }

    private static BufferedEvent due() {
        return BufferedEvent.create(
                SOURCE_ID,
                "api-key",
                Map.of("event_id", "buffered-1", "severity", "high"),
                NOW.minusSeconds(1),
                NOW.minusSeconds(61)
        );
    }

    private static SourceConfig config(Instant ttlExpiresAt) {
        return new SourceConfig(
                SOURCE_ID,
                SOURCE_KEY,
                "hash",
                "http://fm-module:8080",
                FilterRules.of(Map.of()),
                false,
                ttlExpiresAt,
                NOW,
                NOW
        );
    }
}
