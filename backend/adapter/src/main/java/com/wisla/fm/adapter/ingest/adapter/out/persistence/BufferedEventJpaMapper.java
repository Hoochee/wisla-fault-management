package com.wisla.fm.adapter.ingest.adapter.out.persistence;

import com.wisla.fm.adapter.ingest.domain.BufferedEvent;
import org.springframework.stereotype.Component;

/**
 * Hand-written mapper between the {@code buffered_messages} row and the domain model.
 */
@Component
public class BufferedEventJpaMapper {

    public BufferedEvent toDomain(BufferedMessageJpaEntity entity) {
        return new BufferedEvent(
                entity.getId(),
                entity.getSourceId(),
                entity.getIngestApiKey(),
                entity.getPayload(),
                entity.getRetryCount(),
                entity.getNextRetryAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public BufferedMessageJpaEntity toEntity(BufferedEvent event) {
        return new BufferedMessageJpaEntity(
                event.id(),
                event.sourceId(),
                event.ingestApiKey(),
                event.payload(),
                event.retryCount(),
                event.nextRetryAt(),
                event.createdAt(),
                event.updatedAt()
        );
    }
}
