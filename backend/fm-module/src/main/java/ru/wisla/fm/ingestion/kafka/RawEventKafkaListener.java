package ru.wisla.fm.ingestion.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.wisla.fm.configuration.domain.EventSourceEntity;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.ingestion.api.IngestService;

/**
 * Consumes {@code fm.raw-events}. Commit after successful handling (AckMode.RECORD);
 * transient failures throw so the offset is not committed and the record is redelivered.
 * Validation / unknown source are permanent: log and skip (commit).
 * <p>
 * MVP: at-least-once; redelivery may create duplicate {@code RawEvent} rows (no idempotency store).
 */
@Component
public class RawEventKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(RawEventKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final EventSourceRepository eventSourceRepository;
    private final IngestService ingestService;

    public RawEventKafkaListener(ObjectMapper objectMapper,
                                 EventSourceRepository eventSourceRepository,
                                 IngestService ingestService) {
        this.objectMapper = objectMapper;
        this.eventSourceRepository = eventSourceRepository;
        this.ingestService = ingestService;
    }

    @KafkaListener(
            topics = "${wisla.kafka.raw-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String payload) {
        handle(payload);
    }

    /**
     * Package-visible for unit tests of commit/skip/redelivery policy.
     */
    void handle(String payload) {
        RawEventEnvelope envelope;
        try {
            envelope = objectMapper.readValue(payload, RawEventEnvelope.class);
        } catch (Exception e) {
            log.error("Skipping unparseable fm.raw-events message: {}", e.getMessage());
            return;
        }

        if (envelope.sourceId() == null || envelope.body() == null) {
            log.error(
                    "Skipping invalid fm.raw-events envelope messageId={} sourceId={}",
                    envelope.messageId(),
                    envelope.sourceId());
            return;
        }

        EventSourceEntity source = eventSourceRepository.findById(envelope.sourceId()).orElse(null);
        if (source == null || !"active".equalsIgnoreCase(source.getStatus())) {
            log.warn(
                    "Skipping fm.raw-events for unknown/inactive sourceId={} messageId={} sourceKey={}",
                    envelope.sourceId(),
                    envelope.messageId(),
                    envelope.sourceKey());
            return;
        }

        try {
            ingestService.ingest(envelope.body(), envelope.sourceId());
        } catch (IllegalArgumentException e) {
            // Permanent validation / missing source race — skip+commit (do not poison forever).
            log.error(
                    "Skipping permanent ingest failure messageId={} sourceId={}: {}",
                    envelope.messageId(),
                    envelope.sourceId(),
                    e.getMessage());
        }
        // Other runtime exceptions (e.g. transient DB) propagate → no offset commit → redelivery.
    }
}
