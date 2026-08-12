package com.wisla.fm.adapter.ingest.application.service;

import com.wisla.fm.adapter.ingest.application.port.in.DeliverCommand;
import com.wisla.fm.adapter.ingest.application.port.in.ReceiveWebhookCommand;
import com.wisla.fm.adapter.ingest.application.port.out.RawEventPublisherPort;
import com.wisla.fm.adapter.ingest.domain.BufferedEvent;
import com.wisla.fm.adapter.ingest.domain.DeliveryOutcome;
import com.wisla.fm.adapter.ingest.domain.FilterRules;
import com.wisla.fm.adapter.ingest.domain.IngestRejection;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;
import com.wisla.fm.adapter.ingest.testsupport.InMemoryBufferedEventStore;
import com.wisla.fm.adapter.ingest.testsupport.InMemorySourceConfigStore;
import com.wisla.fm.adapter.ingest.testsupport.PrefixingApiKeyVerifier;
import com.wisla.fm.adapter.ingest.testsupport.RecordingRawEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain JUnit 5 with in-memory outbound-port doubles — no Spring context.
 */
class ReceiveWebhookEventServiceTest {

    private static final UUID SOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SOURCE_KEY = "zabbix-prod-01";
    private static final String API_KEY = "source-api-key";
    private static final String ADAPTER_VERSION = "1.2.3";
    private static final int BUFFER_RETRY_BASE_SECONDS = 30;
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemorySourceConfigStore sourceConfigs = new InMemorySourceConfigStore();
    private final InMemoryBufferedEventStore bufferedEvents = new InMemoryBufferedEventStore();
    private final RecordingRawEventPublisher publisher = new RecordingRawEventPublisher();

    private final ReceiveWebhookEventService service = new ReceiveWebhookEventService(
            sourceConfigs,
            bufferedEvents,
            publisher,
            new PrefixingApiKeyVerifier(),
            CLOCK,
            ADAPTER_VERSION,
            BUFFER_RETRY_BASE_SECONDS
    );

    @BeforeEach
    void setUp() {
        sourceConfigs.put(config(FilterRules.of(Map.of("enabled", true)), false, NOW.plusSeconds(3600)));
    }

    // --- source resolution ---------------------------------------------------------------------

    @Test
    void unknownSourceKeyIsRejectedWith404() {
        assertThatThrownBy(() -> service.receive(command("no-such-source", API_KEY, null)))
                .isInstanceOfSatisfying(IngestRejection.class, rejection -> {
                    assertThat(rejection.getErrorCode()).isEqualTo("unknown_source");
                    assertThat(rejection.getHttpStatus()).isEqualTo(404);
                });
    }

    @Test
    void expiredSnapshotIsTreatedAsAnUnknownSource() {
        sourceConfigs.put(config(FilterRules.of(Map.of()), false, NOW.minusSeconds(1)));

        assertThatThrownBy(() -> service.receive(command(SOURCE_KEY, API_KEY, null)))
                .isInstanceOfSatisfying(IngestRejection.class, rejection -> {
                    assertThat(rejection.getErrorCode()).isEqualTo("unknown_source");
                    assertThat(rejection.getHttpStatus()).isEqualTo(404);
                });
    }

    // --- api key validation --------------------------------------------------------------------

    @Test
    void mismatchedHeaderAndQueryKeysAreRejectedWith401InvalidSourceKey() {
        assertThatThrownBy(() -> service.receive(command(SOURCE_KEY, API_KEY, "other-key")))
                .isInstanceOfSatisfying(IngestRejection.class, rejection -> {
                    assertThat(rejection.getErrorCode()).isEqualTo("invalid_source_key");
                    assertThat(rejection.getHttpStatus()).isEqualTo(401);
                });
    }

    @Test
    void missingApiKeyIsRejectedWith401MissingApiKey() {
        assertThatThrownBy(() -> service.receive(command(SOURCE_KEY, null, null)))
                .isInstanceOfSatisfying(IngestRejection.class, rejection -> {
                    assertThat(rejection.getErrorCode()).isEqualTo("missing_api_key");
                    assertThat(rejection.getHttpStatus()).isEqualTo(401);
                });
    }

    @Test
    void blankApiKeyIsRejectedWith401MissingApiKey() {
        assertThatThrownBy(() -> service.receive(command(SOURCE_KEY, "   ", null)))
                .isInstanceOfSatisfying(IngestRejection.class, rejection ->
                        assertThat(rejection.getErrorCode()).isEqualTo("missing_api_key"));
    }

    @Test
    void wrongApiKeyIsRejectedWith401InvalidSourceKey() {
        assertThatThrownBy(() -> service.receive(command(SOURCE_KEY, "wrong-key", null)))
                .isInstanceOfSatisfying(IngestRejection.class, rejection -> {
                    assertThat(rejection.getErrorCode()).isEqualTo("invalid_source_key");
                    assertThat(rejection.getHttpStatus()).isEqualTo(401);
                });
    }

    @Test
    void apiKeyFromTheQueryParameterIsAccepted() {
        DeliveryOutcome outcome = service.receive(command(SOURCE_KEY, null, API_KEY));

        assertThat(outcome.delivery()).isEqualTo("forwarded");
    }

    @Test
    void identicalHeaderAndQueryKeysAreAccepted() {
        DeliveryOutcome outcome = service.receive(command(SOURCE_KEY, API_KEY, API_KEY));

        assertThat(outcome.delivery()).isEqualTo("forwarded");
    }

    // --- blocked source ------------------------------------------------------------------------

    @Test
    void blockedSourceIsRejectedWith403SourceBlocked() {
        sourceConfigs.put(config(FilterRules.of(Map.of()), true, NOW.plusSeconds(3600)));

        assertThatThrownBy(() -> service.receive(command(SOURCE_KEY, API_KEY, null)))
                .isInstanceOfSatisfying(IngestRejection.class, rejection -> {
                    assertThat(rejection.getErrorCode()).isEqualTo("source_blocked");
                    assertThat(rejection.getHttpStatus()).isEqualTo(403);
                });
    }

    /** The api key is validated before the blocked check, exactly as today. */
    @Test
    void blockedSourceStillReportsAKeyProblemFirst() {
        sourceConfigs.put(config(FilterRules.of(Map.of()), true, NOW.plusSeconds(3600)));

        assertThatThrownBy(() -> service.receive(command(SOURCE_KEY, "wrong-key", null)))
                .isInstanceOfSatisfying(IngestRejection.class, rejection ->
                        assertThat(rejection.getErrorCode()).isEqualTo("invalid_source_key"));
    }

    // --- pre-filter ----------------------------------------------------------------------------

    @Test
    void filteredPayloadIsRejectedWith400FilteredAndNeverPublished() {
        sourceConfigs.put(config(
                FilterRules.of(Map.of(
                        "enabled", true,
                        "drop_if", List.of(Map.of("field", "severity", "op", "eq", "value", "low"))
                )),
                false,
                NOW.plusSeconds(3600)
        ));

        assertThatThrownBy(() -> service.receive(command(SOURCE_KEY, API_KEY, null, payload("severity", "low"))))
                .isInstanceOfSatisfying(IngestRejection.class, rejection -> {
                    assertThat(rejection.getErrorCode()).isEqualTo("filtered");
                    assertThat(rejection.getHttpStatus()).isEqualTo(400);
                });
        assertThat(publisher.publishCount()).isZero();
        assertThat(bufferedEvents.count()).isZero();
    }

    // --- delivery ------------------------------------------------------------------------------

    @Test
    void successfulPublishIsForwardedWithoutAMessageId() {
        DeliveryOutcome outcome = service.receive(command(SOURCE_KEY, API_KEY, null));

        assertThat(outcome.delivery()).isEqualTo("forwarded");
        assertThat(outcome.messageId()).isNull();
        assertThat(bufferedEvents.count()).isZero();
        assertThat(publisher.last().sourceId()).isEqualTo(SOURCE_ID);
        assertThat(publisher.last().sourceKey()).isEqualTo(SOURCE_KEY);
    }

    @Test
    void publishedBodyIsTheNormalizedIngestRequest() {
        service.receive(command(SOURCE_KEY, API_KEY, null, payload("event_id", "evt-1", "severity", "high")));

        Map<String, Object> body = publisher.last().ingestBody();
        assertThat(body).containsOnlyKeys("events", "adapterVersion", "receivedAt");
        assertThat(body).containsEntry("adapterVersion", ADAPTER_VERSION);
        assertThat(body).containsEntry("receivedAt", NOW.toString());
    }

    @Test
    void retryablePublishFailureBuffersTheOriginalPayloadAndReturnsItsMessageId() {
        publisher.stub(RawEventPublisherPort.PublishResult.retryable("broker down"));
        Map<String, Object> payload = payload("event_id", "evt-2");

        DeliveryOutcome outcome = service.receive(command(SOURCE_KEY, API_KEY, null, payload));

        assertThat(outcome.delivery()).isEqualTo("buffered");
        assertThat(outcome.messageId()).isNotNull();
        assertThat(bufferedEvents.all()).hasSize(1);
        BufferedEvent buffered = bufferedEvents.all().getFirst();
        assertThat(buffered.id()).isEqualTo(outcome.messageId());
        assertThat(buffered.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(buffered.ingestApiKey()).isEqualTo(API_KEY);
        assertThat(buffered.payload()).isSameAs(payload);
        assertThat(buffered.retryCount()).isZero();
        assertThat(buffered.nextRetryAt()).isEqualTo(NOW.plusSeconds(BUFFER_RETRY_BASE_SECONDS));
        assertThat(buffered.createdAt()).isEqualTo(NOW);
    }

    @Test
    void permanentPublishFailureIsRejectedWith502IngestRejectedAndNeverBuffered() {
        publisher.stub(RawEventPublisherPort.PublishResult.permanent("serialization failed"));

        assertThatThrownBy(() -> service.receive(command(SOURCE_KEY, API_KEY, null)))
                .isInstanceOfSatisfying(IngestRejection.class, rejection -> {
                    assertThat(rejection.getErrorCode()).isEqualTo("ingest_rejected");
                    assertThat(rejection.getHttpStatus()).isEqualTo(502);
                    assertThat(rejection).hasMessage("serialization failed");
                });
        assertThat(bufferedEvents.count()).isZero();
    }

    @Test
    void permanentPublishFailureWithoutAnErrorUsesTheDefaultMessage() {
        publisher.stub(RawEventPublisherPort.PublishResult.permanent(null));

        assertThatThrownBy(() -> service.receive(command(SOURCE_KEY, API_KEY, null)))
                .isInstanceOfSatisfying(IngestRejection.class, rejection ->
                        assertThat(rejection).hasMessage("Kafka publish rejected"));
    }

    // --- deliver use case (probe path) ---------------------------------------------------------

    @Test
    void deliverSkipsSourceLookupKeyValidationAndFiltering() {
        SourceConfig blockedAndFiltering = config(
                FilterRules.of(Map.of(
                        "enabled", true,
                        "drop_if", List.of(Map.of("field", "probe", "op", "exists", "value", ""))
                )),
                true,
                NOW.minusSeconds(1)
        );

        DeliveryOutcome outcome = service.deliver(
                new DeliverCommand(blockedAndFiltering, API_KEY, payload("probe", true)));

        assertThat(outcome.delivery()).isEqualTo("forwarded");
        assertThat(publisher.last().ingestBody()).containsEntry("heartbeat", true);
    }

    private ReceiveWebhookCommand command(String sourceKey, String headerApiKey, String queryApiKey) {
        return command(sourceKey, headerApiKey, queryApiKey, payload("event_id", "evt-1"));
    }

    private ReceiveWebhookCommand command(
            String sourceKey,
            String headerApiKey,
            String queryApiKey,
            Map<String, Object> payload
    ) {
        return new ReceiveWebhookCommand(sourceKey, headerApiKey, queryApiKey, payload);
    }

    private static SourceConfig config(FilterRules filterRules, boolean blocked, Instant ttlExpiresAt) {
        return new SourceConfig(
                SOURCE_ID,
                SOURCE_KEY,
                PrefixingApiKeyVerifier.hash(API_KEY),
                "http://fm-module:8080",
                filterRules,
                blocked,
                ttlExpiresAt,
                NOW,
                NOW
        );
    }

    private static Map<String, Object> payload(Object... keyValuePairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            payload.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return payload;
    }
}
