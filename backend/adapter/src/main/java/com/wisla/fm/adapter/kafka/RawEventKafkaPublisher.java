package com.wisla.fm.adapter.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisla.fm.adapter.config.KafkaIngestProperties;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class RawEventKafkaPublisher implements RawEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RawEventKafkaPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaIngestProperties kafkaProperties;
    private final ObjectMapper objectMapper;

    public RawEventKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaIngestProperties kafkaProperties,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public PublishResult publish(UUID sourceId, String sourceKey, Map<String, Object> ingestBody) {
        RawEventEnvelope envelope = RawEventEnvelopeCodec.create(sourceId, sourceKey, ingestBody);
        String json;
        try {
            json = RawEventEnvelopeCodec.serialize(objectMapper, envelope);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize raw-event envelope for sourceId={}", sourceId, ex);
            return PublishResult.permanent(ex.getMessage());
        }

        String topic = kafkaProperties.rawEventsTopic();
        String key = sourceId.toString();
        try {
            SendResult<String, String> result = kafkaTemplate
                    .send(topic, key, json)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug(
                    "Published raw event messageId={} sourceId={} topic={} partition={}",
                    envelope.messageId(),
                    sourceId,
                    topic,
                    result.getRecordMetadata().partition()
            );
            return PublishResult.ok();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return PublishResult.retryable(ex.getMessage());
        } catch (java.util.concurrent.TimeoutException ex) {
            return PublishResult.retryable(ex.getMessage());
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (isRetryable(cause)) {
                return PublishResult.retryable(cause.getMessage());
            }
            return PublishResult.permanent(cause.getMessage());
        } catch (SerializationException ex) {
            return PublishResult.permanent(ex.getMessage());
        } catch (Exception ex) {
            if (isRetryable(ex)) {
                return PublishResult.retryable(ex.getMessage());
            }
            return PublishResult.permanent(ex.getMessage());
        }
    }

    private boolean isRetryable(Throwable ex) {
        return ex instanceof java.util.concurrent.TimeoutException
                || ex instanceof org.apache.kafka.common.errors.TimeoutException
                || ex instanceof RetriableException
                || ex instanceof KafkaException;
    }
}
