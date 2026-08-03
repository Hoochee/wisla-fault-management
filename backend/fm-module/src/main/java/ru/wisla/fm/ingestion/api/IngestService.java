package ru.wisla.fm.ingestion.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.configuration.domain.EventSourceEntity;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.ingestion.domain.RawEventEntity;
import ru.wisla.fm.ingestion.persistence.RawEventRepository;
import ru.wisla.fm.processing.service.EventProcessingService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestService {

    private final RawEventRepository rawEventRepository;
    private final EventSourceRepository eventSourceRepository;
    private final EventProcessingService eventProcessingService;
    private final ObjectMapper objectMapper;

    public IngestService(RawEventRepository rawEventRepository,
                         EventSourceRepository eventSourceRepository,
                         EventProcessingService eventProcessingService,
                         ObjectMapper objectMapper) {
        this.rawEventRepository = rawEventRepository;
        this.eventSourceRepository = eventSourceRepository;
        this.eventProcessingService = eventProcessingService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public IngestResponse ingest(IngestRequest request, Authentication authentication) {
        UUID sourceId = (UUID) authentication.getPrincipal();
        return ingest(request, sourceId);
    }

    /**
     * Shared ingest entry for HTTP (after API-key auth) and Kafka consumer (trusted {@code sourceId}).
     */
    @Transactional
    public IngestResponse ingest(IngestRequest request, UUID sourceId) {
        EventSourceEntity source = eventSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source not found"));

        if (Boolean.TRUE.equals(request.heartbeat())) {
            source.setLastSuccessAt(Instant.now());
            if (request.adapterVersion() != null) {
                source.setAdapterVersion(request.adapterVersion());
            }
            eventSourceRepository.save(source);
            return new IngestResponse(0, 0, List.of(), true);
        }

        UUID batchId = UUID.randomUUID();
        List<UUID> rawEventIds = new ArrayList<>();
        int accepted = 0;
        int rejected = 0;

        List<IngestRequest.IngestEventPayload> events = request.events() != null ? request.events() : List.of();
        for (IngestRequest.IngestEventPayload payload : events) {
            try {
                RawEventEntity entity = new RawEventEntity();
                entity.setSourceId(sourceId);
                entity.setExternalId(payload.externalId());
                entity.setTitle(payload.title());
                entity.setDescription(payload.description());
                entity.setSeverity(payload.severity());
                entity.setStatus(payload.status() != null ? payload.status() : "new");
                entity.setNodeFqdn(payload.nodeFqdn());
                entity.setSourceAt(payload.occurredAt());
                entity.setIngestBatchId(batchId);
                entity.setPayload(toJson(payload.attributes()));
                entity.setRawPayload(toJson(payload.rawPayload() != null ? payload.rawPayload() : Map.of()));
                rawEventIds.add(rawEventRepository.save(entity).getId());
                accepted++;
            } catch (Exception e) {
                rejected++;
            }
        }

        source.setLastSuccessAt(Instant.now());
        if (request.adapterVersion() != null) {
            source.setAdapterVersion(request.adapterVersion());
        }
        eventSourceRepository.save(source);

        if (!rawEventIds.isEmpty()) {
            eventProcessingService.processBatch(rawEventIds);
        }

        return new IngestResponse(accepted, rejected, rawEventIds, null);
    }

    private String toJson(Map<String, Object> map) throws JsonProcessingException {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        return objectMapper.writeValueAsString(map);
    }
}
