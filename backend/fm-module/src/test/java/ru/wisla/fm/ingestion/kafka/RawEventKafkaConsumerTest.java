package ru.wisla.fm.ingestion.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import ru.wisla.fm.configuration.domain.EventSourceEntity;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.ingestion.api.IngestRequest;
import ru.wisla.fm.ingestion.persistence.RawEventRepository;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@EmbeddedKafka(partitions = 1, topics = {"fm.raw-events"})
@TestPropertySource(
        properties = {
            "spring.kafka.listener.auto-startup=true",
            "spring.kafka.consumer.group-id=fm-module-ingestion-it",
            "spring.kafka.consumer.auto-offset-reset=earliest"
        })
class RawEventKafkaConsumerTest extends AbstractFmModuleTest {

    @Autowired private EventSourceRepository eventSourceRepository;
    @Autowired private RawEventRepository rawEventRepository;
    @Autowired private org.springframework.kafka.test.EmbeddedKafkaBroker embeddedKafka;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUpProducer() {
        Map<String, Object> producerProps = new HashMap<>(KafkaTestUtils.producerProps(embeddedKafka));
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
    }

    @Test
    void eventEnvelopeCreatesRawEvents() throws Exception {
        EventSourceEntity source = eventSourceRepository.findByWebhookPathKey("demo").orElseThrow();
        long before = countForSource(source.getId());
        String externalId = "kafka-evt-" + System.nanoTime();

        publish(envelope(source.getId(), "demo", eventBody(externalId)));

        awaitUntil(() -> countForSource(source.getId()) > before, Duration.ofSeconds(15));

        assertThat(rawEventRepository.findAll().stream()
                        .filter(r -> source.getId().equals(r.getSourceId()))
                        .anyMatch(r -> externalId.equals(r.getExternalId())))
                .isTrue();

        EventSourceEntity updated = eventSourceRepository.findById(source.getId()).orElseThrow();
        assertThat(updated.getLastSuccessAt()).isNotNull();
        assertThat(updated.getAdapterVersion()).isEqualTo("kafka-it-1.0.0");
    }

    @Test
    void heartbeatEnvelopeUpdatesSourceWithoutRawEvents() throws Exception {
        EventSourceEntity source = eventSourceRepository.findByWebhookPathKey("demo").orElseThrow();
        long before = countForSource(source.getId());
        String hbVersion = "kafka-hb-" + System.nanoTime();
        Instant beforeSuccess = source.getLastSuccessAt();

        IngestRequest heartbeat = new IngestRequest(true, List.of(), hbVersion, Instant.now());
        publish(envelope(source.getId(), "demo", heartbeat));

        awaitUntil(
                () -> {
                    EventSourceEntity refreshed =
                            eventSourceRepository.findById(source.getId()).orElseThrow();
                    return hbVersion.equals(refreshed.getAdapterVersion())
                            && refreshed.getLastSuccessAt() != null
                            && (beforeSuccess == null
                                    || !refreshed.getLastSuccessAt().isBefore(beforeSuccess));
                },
                Duration.ofSeconds(15));

        assertThat(countForSource(source.getId())).isEqualTo(before);
    }

    @Test
    void unknownSourceIdDoesNotCreateRawEventsAndConsumerRecovers() throws Exception {
        EventSourceEntity source = eventSourceRepository.findByWebhookPathKey("demo").orElseThrow();
        long before = rawEventRepository.count();
        UUID unknown = UUID.randomUUID();

        publish(envelope(unknown, "missing", eventBody("unknown-" + System.nanoTime())));
        Thread.sleep(1500);
        assertThat(rawEventRepository.count()).isEqualTo(before);

        String externalId = "recover-evt-" + System.nanoTime();
        publish(envelope(source.getId(), "demo", eventBody(externalId)));
        awaitUntil(
                () -> rawEventRepository.findAll().stream()
                        .anyMatch(r -> externalId.equals(r.getExternalId())),
                Duration.ofSeconds(15));
    }

    private RawEventEnvelope envelope(UUID sourceId, String sourceKey, IngestRequest body) {
        return new RawEventEnvelope(
                1, UUID.randomUUID(), Instant.now(), sourceId, sourceKey, body);
    }

    private IngestRequest eventBody(String externalId) {
        return new IngestRequest(
                false,
                List.of(new IngestRequest.IngestEventPayload(
                        externalId,
                        "Kafka disk full",
                        "from EmbeddedKafka",
                        "critical",
                        "new",
                        Instant.now(),
                        "demo-server.wisla.local",
                        Map.of("via", "kafka"),
                        Map.of("raw", true))),
                "kafka-it-1.0.0",
                Instant.now());
    }

    private void publish(RawEventEnvelope envelope) throws Exception {
        String json = objectMapper.writeValueAsString(envelope);
        kafkaTemplate.send("fm.raw-events", envelope.sourceId().toString(), json).get();
    }

    private long countForSource(UUID sourceId) {
        return rawEventRepository.findAll().stream()
                .filter(r -> sourceId.equals(r.getSourceId()))
                .count();
    }

    private static void awaitUntil(BooleanSupplier condition, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Condition not met within " + timeout);
    }
}
