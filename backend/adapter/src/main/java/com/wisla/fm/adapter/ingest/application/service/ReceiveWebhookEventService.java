package com.wisla.fm.adapter.ingest.application.service;

import com.wisla.fm.adapter.ingest.application.port.in.DeliverCommand;
import com.wisla.fm.adapter.ingest.application.port.in.DeliverIngestEventUseCase;
import com.wisla.fm.adapter.ingest.application.port.in.ReceiveWebhookCommand;
import com.wisla.fm.adapter.ingest.application.port.in.ReceiveWebhookEventUseCase;
import com.wisla.fm.adapter.ingest.application.port.out.ApiKeyVerifierPort;
import com.wisla.fm.adapter.ingest.application.port.out.BufferedEventStorePort;
import com.wisla.fm.adapter.ingest.application.port.out.RawEventPublisherPort;
import com.wisla.fm.adapter.ingest.application.port.out.SourceConfigLookupPort;
import com.wisla.fm.adapter.ingest.domain.BufferedEvent;
import com.wisla.fm.adapter.ingest.domain.DeliveryOutcome;
import com.wisla.fm.adapter.ingest.domain.IngestPayloadNormalizer;
import com.wisla.fm.adapter.ingest.domain.IngestRejection;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

public class ReceiveWebhookEventService implements ReceiveWebhookEventUseCase, DeliverIngestEventUseCase {

    private final SourceConfigLookupPort sourceConfigLookup;
    private final BufferedEventStorePort bufferedEventStore;
    private final RawEventPublisherPort rawEventPublisher;
    private final ApiKeyVerifierPort apiKeyVerifier;
    private final Clock clock;
    private final String adapterVersion;
    private final int bufferRetryBaseSeconds;

    public ReceiveWebhookEventService(
            SourceConfigLookupPort sourceConfigLookup,
            BufferedEventStorePort bufferedEventStore,
            RawEventPublisherPort rawEventPublisher,
            ApiKeyVerifierPort apiKeyVerifier,
            Clock clock,
            String adapterVersion,
            int bufferRetryBaseSeconds
    ) {
        this.sourceConfigLookup = sourceConfigLookup;
        this.bufferedEventStore = bufferedEventStore;
        this.rawEventPublisher = rawEventPublisher;
        this.apiKeyVerifier = apiKeyVerifier;
        this.clock = clock;
        this.adapterVersion = adapterVersion;
        this.bufferRetryBaseSeconds = bufferRetryBaseSeconds;
    }

    @Override
    public DeliveryOutcome receive(ReceiveWebhookCommand command) {
        SourceConfig config = requireFreshConfig(command.sourceKey());

        String ingestApiKey = validateApiKey(command, config);
        ensureSourceActive(config);

        if (config.filterRules().shouldDrop(command.payload())) {
            throw new IngestRejection("filtered", "Event rejected by pre-filter rules", 400);
        }

        return deliver(new DeliverCommand(config, ingestApiKey, command.payload()));
    }

    @Override
    public DeliveryOutcome deliver(DeliverCommand command) {
        SourceConfig config = command.config();
        Map<String, Object> ingestBody =
                IngestPayloadNormalizer.normalize(command.payload(), adapterVersion, clock);
        RawEventPublisherPort.PublishResult result = rawEventPublisher.publish(
                config.sourceId(),
                config.sourceKey(),
                ingestBody
        );

        if (result.success()) {
            return DeliveryOutcome.forwarded();
        }

        if (!result.retryable()) {
            throw new IngestRejection(
                    "ingest_rejected",
                    result.error() != null ? result.error() : "Kafka publish rejected",
                    502
            );
        }

        Instant now = clock.instant();
        BufferedEvent buffered = bufferedEventStore.save(BufferedEvent.create(
                config.sourceId(),
                command.ingestApiKey(),
                command.payload(),
                now.plusSeconds(bufferRetryBaseSeconds),
                now
        ));
        return DeliveryOutcome.buffered(buffered.id());
    }

    private SourceConfig requireFreshConfig(String sourceKey) {
        return sourceConfigLookup.findBySourceKey(sourceKey)
                .filter(config -> !config.isExpired(clock))
                .orElseThrow(() -> new IngestRejection(
                        "unknown_source",
                        "Source configuration not found or expired for key: " + sourceKey,
                        404
                ));
    }

    private String validateApiKey(ReceiveWebhookCommand command, SourceConfig config) {
        String headerApiKey = command.headerApiKey();
        String queryApiKey = command.queryApiKey();

        if (headerApiKey != null && queryApiKey != null && !headerApiKey.equals(queryApiKey)) {
            throw new IngestRejection(
                    "invalid_source_key",
                    "Header and query API keys do not match",
                    401
            );
        }

        String providedKey = headerApiKey != null ? headerApiKey : queryApiKey;
        if (providedKey == null || providedKey.isBlank()) {
            throw new IngestRejection(
                    "missing_api_key",
                    "API key is required via X-Source-Key header or sourceKey query parameter",
                    401
            );
        }

        if (!apiKeyVerifier.matches(providedKey, config.apiKeyHash())) {
            throw new IngestRejection(
                    "invalid_source_key",
                    "API key does not match source configuration",
                    401
            );
        }

        return providedKey;
    }

    private void ensureSourceActive(SourceConfig config) {
        if (config.blocked()) {
            throw new IngestRejection(
                    "source_blocked",
                    "Source is blocked due to event storm",
                    403
            );
        }
    }
}
