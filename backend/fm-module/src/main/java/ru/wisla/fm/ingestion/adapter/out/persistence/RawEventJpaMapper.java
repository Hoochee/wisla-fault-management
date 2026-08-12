package ru.wisla.fm.ingestion.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.wisla.fm.ingestion.domain.RawEvent;

import java.util.Map;

/**
 * Hand-written mapper between the ingestion domain model and the {@code raw_events} row.
 * It owns the jsonb serialization of {@code attributes} / {@code rawPayload}.
 */
@Component
public class RawEventJpaMapper {

    private static final String EMPTY_JSON = "{}";
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public RawEventJpaMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RawEventJpaEntity toJpaEntity(RawEvent rawEvent) {
        RawEventJpaEntity entity = new RawEventJpaEntity();
        entity.setSourceId(rawEvent.sourceId());
        entity.setExternalId(rawEvent.externalId());
        entity.setTitle(rawEvent.title());
        entity.setDescription(rawEvent.description());
        entity.setSeverity(rawEvent.severity());
        entity.setStatus(rawEvent.status());
        entity.setNodeFqdn(rawEvent.nodeFqdn());
        entity.setCiId(rawEvent.ciId());
        entity.setSourceAt(rawEvent.sourceAt());
        entity.setIngestBatchId(rawEvent.ingestBatchId());
        entity.setProcessed(rawEvent.processed());
        entity.setProcessedEventId(rawEvent.processedEventId());
        entity.setProcessingError(rawEvent.processingError());
        entity.setPayload(toJson(rawEvent.attributes()));
        entity.setRawPayload(toJson(rawEvent.rawPayload()));
        return entity;
    }

    public RawEvent toDomain(RawEventJpaEntity entity) {
        return new RawEvent(
                entity.getId(),
                entity.getSourceId(),
                entity.getExternalId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getSeverity(),
                entity.getStatus(),
                entity.getNodeFqdn(),
                entity.getCiId(),
                fromJson(entity.getPayload()),
                fromJson(entity.getRawPayload()),
                entity.getSourceAt(),
                entity.getIngestBatchId(),
                entity.isProcessed(),
                entity.getProcessedEventId(),
                entity.getProcessingError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return EMPTY_JSON;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize raw event payload", e);
        }
    }

    /**
     * The read path never exposes the payload, so a row whose jsonb cannot be parsed must not fail
     * the whole listing.
     */
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, JSON_OBJECT);
            return parsed != null ? parsed : Map.of();
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
