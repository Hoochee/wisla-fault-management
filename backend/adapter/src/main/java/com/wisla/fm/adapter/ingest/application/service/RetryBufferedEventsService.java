package com.wisla.fm.adapter.ingest.application.service;

import com.wisla.fm.adapter.ingest.application.port.in.RetryBufferedEventsUseCase;
import com.wisla.fm.adapter.ingest.application.port.out.BufferedEventStorePort;
import com.wisla.fm.adapter.ingest.application.port.out.RawEventPublisherPort;
import com.wisla.fm.adapter.ingest.application.port.out.SourceConfigLookupPort;
import com.wisla.fm.adapter.ingest.domain.BufferedEvent;
import com.wisla.fm.adapter.ingest.domain.IngestPayloadNormalizer;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public class RetryBufferedEventsService implements RetryBufferedEventsUseCase {

    private final SourceConfigLookupPort sourceConfigLookup;
    private final BufferedEventStorePort bufferedEventStore;
    private final RawEventPublisherPort rawEventPublisher;
    private final Clock clock;
    private final String adapterVersion;
    private final int bufferRetryBaseSeconds;

    public RetryBufferedEventsService(
            SourceConfigLookupPort sourceConfigLookup,
            BufferedEventStorePort bufferedEventStore,
            RawEventPublisherPort rawEventPublisher,
            Clock clock,
            String adapterVersion,
            int bufferRetryBaseSeconds
    ) {
        this.sourceConfigLookup = sourceConfigLookup;
        this.bufferedEventStore = bufferedEventStore;
        this.rawEventPublisher = rawEventPublisher;
        this.clock = clock;
        this.adapterVersion = adapterVersion;
        this.bufferRetryBaseSeconds = bufferRetryBaseSeconds;
    }

    @Override
    public void retryDueMessages(Instant now) {
        for (BufferedEvent event : bufferedEventStore.findDue(now)) {
            retry(event);
        }
    }

    private void retry(BufferedEvent event) {
        // The snapshot TTL is deliberately not applied here: an expired snapshot still drives the
        // retry, matching the pre-refactor worker.
        Optional<SourceConfig> configOpt = sourceConfigLookup.findBySourceId(event.sourceId());
        if (configOpt.isEmpty()) {
            reschedule(event);
            return;
        }

        SourceConfig config = configOpt.get();
        Map<String, Object> ingestBody =
                IngestPayloadNormalizer.normalize(event.payload(), adapterVersion, clock);
        RawEventPublisherPort.PublishResult result = rawEventPublisher.publish(
                config.sourceId(),
                config.sourceKey(),
                ingestBody
        );

        if (result.success()) {
            bufferedEventStore.delete(event);
            return;
        }

        if (!result.retryable()) {
            bufferedEventStore.delete(event);
            return;
        }

        reschedule(event);
    }

    private void reschedule(BufferedEvent event) {
        event.scheduleRetry(bufferRetryBaseSeconds, clock.instant());
        bufferedEventStore.save(event);
    }
}
