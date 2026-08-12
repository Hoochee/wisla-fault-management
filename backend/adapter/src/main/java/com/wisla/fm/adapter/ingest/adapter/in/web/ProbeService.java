package com.wisla.fm.adapter.ingest.adapter.in.web;

import com.wisla.fm.adapter.ingest.adapter.in.web.dto.ProbeRequest;
import com.wisla.fm.adapter.ingest.adapter.in.web.dto.ProbeResponse;
import com.wisla.fm.adapter.ingest.application.port.in.DeliverCommand;
import com.wisla.fm.adapter.ingest.application.port.in.DeliverIngestEventUseCase;
import com.wisla.fm.adapter.ingest.domain.DeliveryOutcome;
import com.wisla.fm.adapter.ingest.domain.IngestRejection;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProbeService {

    private final SourceConfigSnapshotReader sourceConfigSnapshotReader;
    private final DeliverIngestEventUseCase deliverIngestEvent;

    public ProbeService(
            SourceConfigSnapshotReader sourceConfigSnapshotReader,
            DeliverIngestEventUseCase deliverIngestEvent
    ) {
        this.sourceConfigSnapshotReader = sourceConfigSnapshotReader;
        this.deliverIngestEvent = deliverIngestEvent;
    }

    public ProbeResponse execute(ProbeRequest request) {
        SourceConfig config = sourceConfigSnapshotReader.requireBySourceId(request.source_id());

        if (config.blocked()) {
            throw new IngestRejection("source_blocked", "Source is blocked", 422);
        }

        String ingestApiKey = request.ingest_api_key();
        if (ingestApiKey == null || ingestApiKey.isBlank()) {
            return new ProbeResponse(
                    false,
                    request.source_id(),
                    Instant.now(),
                    "failed",
                    null,
                    "ingest_api_key is required for Kafka delivery test",
                    0L
            );
        }

        Map<String, Object> payload = request.test_payload() != null
                ? request.test_payload()
                : defaultProbePayload(config.sourceKey());

        long start = System.currentTimeMillis();
        try {
            DeliveryOutcome delivery = deliverIngestEvent.deliver(
                    new DeliverCommand(config, ingestApiKey, payload));
            long latency = System.currentTimeMillis() - start;
            boolean success = delivery.isForwarded();
            return new ProbeResponse(
                    success,
                    request.source_id(),
                    Instant.now(),
                    delivery.delivery(),
                    null,
                    success ? null : "Kafka unavailable, message buffered",
                    latency
            );
        } catch (IngestRejection ex) {
            long latency = System.currentTimeMillis() - start;
            return new ProbeResponse(
                    false,
                    request.source_id(),
                    Instant.now(),
                    "failed",
                    ex.getHttpStatus(),
                    ex.getMessage(),
                    latency
            );
        }
    }

    private Map<String, Object> defaultProbePayload(String sourceKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("probe", true);
        payload.put("source_key", sourceKey);
        payload.put("message", "WISLA FM adapter connectivity probe");
        payload.put("timestamp", Instant.now().toString());
        return payload;
    }
}
