package ru.wisla.fm.ingestion.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.ingestion.application.port.in.IngestEventsUseCase;
import ru.wisla.fm.ingestion.application.port.out.EventSourceStatePort;
import ru.wisla.fm.ingestion.domain.SourceIngestState;

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
    private final EventSourceStatePort eventSourceState;
    private final IngestEventsUseCase ingestEvents;

    public RawEventKafkaListener(ObjectMapper objectMapper,
                                 EventSourceStatePort eventSourceState,
                                 IngestEventsUseCase ingestEvents) {
        this.objectMapper = objectMapper;
        this.eventSourceState = eventSourceState;
        this.ingestEvents = ingestEvents;
    }

    /**
     * Declares the ingest transaction for the Kafka path: storing the raw events and processing the
     * batch commit or roll back together.
     */
    @KafkaListener(
            topics = "${wisla.kafka.raw-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
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

        SourceIngestState source = eventSourceState.find(envelope.sourceId()).orElse(null);
        if (source == null || !source.isActive()) {
            log.warn(
                    "Skipping fm.raw-events for unknown/inactive sourceId={} messageId={} sourceKey={}",
                    envelope.sourceId(),
                    envelope.messageId(),
                    envelope.sourceKey());
            return;
        }

        try {
            ingestEvents.ingest(envelope.body().toCommand(envelope.sourceId()));
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
