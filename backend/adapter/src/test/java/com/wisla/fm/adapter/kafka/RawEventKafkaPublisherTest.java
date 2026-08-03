package com.wisla.fm.adapter.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisla.fm.adapter.config.KafkaIngestProperties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(partitions = 1, topics = {"fm.raw-events"})
class RawEventKafkaPublisherTest {

    @Autowired
    private RawEventKafkaPublisher publisher;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publishAcknowledgedByBrokerIsSuccess() throws Exception {
        UUID sourceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("events", List.of(Map.of(
                "externalId", "1",
                "title", "t",
                "severity", "minor",
                "occurredAt", "2026-06-23T10:00:00Z"
        )));
        body.put("adapterVersion", "1.0.0");
        body.put("receivedAt", "2026-06-23T10:00:01Z");

        RawEventPublisher.PublishResult result = publisher.publish(sourceId, "zabbix-prod-01", body);

        assertThat(result.success()).isTrue();
        assertThat(result.retryable()).isFalse();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("publisher-test", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (var consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "fm.raw-events");
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, "fm.raw-events", Duration.ofSeconds(10));
            assertThat(record.key()).isEqualTo(sourceId.toString());
            RawEventEnvelope envelope = RawEventEnvelopeCodec.deserialize(objectMapper, record.value());
            assertThat(envelope.sourceId()).isEqualTo(sourceId);
            assertThat(envelope.sourceKey()).isEqualTo("zabbix-prod-01");
            assertThat(envelope.schemaVersion()).isEqualTo(1);
            assertThat(record.value()).doesNotContain("apiKey");
        }
    }

    @Test
    void publishSignalsRetryableWhenBrokerSendFails() {
        KafkaTemplate<String, String> template = failingTemplate(
                CompletableFuture.failedFuture(new org.springframework.kafka.KafkaException("broker down"))
        );

        RawEventKafkaPublisher failingPublisher = new RawEventKafkaPublisher(
                template,
                new KafkaIngestProperties("fm.raw-events"),
                objectMapper
        );

        RawEventPublisher.PublishResult result = failingPublisher.publish(
                UUID.randomUUID(),
                "src",
                Map.of("heartbeat", true)
        );

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void publishSignalsPermanentWhenSerializationFails() {
        ObjectMapper brokenMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.databind.JsonMappingException(null, "permanent serialize failure");
            }
        };

        KafkaTemplate<String, String> template = failingTemplate(CompletableFuture.completedFuture(null));

        RawEventKafkaPublisher failingPublisher = new RawEventKafkaPublisher(
                template,
                new KafkaIngestProperties("fm.raw-events"),
                brokenMapper
        );

        RawEventPublisher.PublishResult result = failingPublisher.publish(
                UUID.randomUUID(),
                "src",
                Map.of("events", List.of())
        );

        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
    }

    private static KafkaTemplate<String, String> failingTemplate(CompletableFuture<SendResult<String, String>> future) {
        return new KafkaTemplate<>(new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(Map.of(
                org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class,
                org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class
        ))) {
            @Override
            public CompletableFuture<SendResult<String, String>> send(String topic, String key, String data) {
                return future;
            }
        };
    }
}
