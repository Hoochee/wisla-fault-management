package com.wisla.fm.adapter.service;

import com.wisla.fm.adapter.config.AdapterProperties;
import com.wisla.fm.adapter.persistence.entity.SourceConfigSnapshot;
import com.wisla.fm.adapter.web.dto.ProbeRequest;
import com.wisla.fm.adapter.web.dto.ProbeResponse;
import com.wisla.fm.adapter.web.dto.WebhookAcceptedResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ProbeService {

    private final SourceConfigService sourceConfigService;
    private final WebhookService webhookService;

    public ProbeService(SourceConfigService sourceConfigService, WebhookService webhookService) {
        this.sourceConfigService = sourceConfigService;
        this.webhookService = webhookService;
    }

    public ProbeResponse execute(ProbeRequest request) {
        SourceConfigSnapshot config = sourceConfigService.requireBySourceId(request.source_id());

        if (config.isBlocked()) {
            throw new AdapterException("source_blocked", "Source is blocked", 422);
        }

        String ingestApiKey = request.ingest_api_key();
        if (ingestApiKey == null || ingestApiKey.isBlank()) {
            return new ProbeResponse(
                    false,
                    request.source_id(),
                    Instant.now(),
                    "failed",
                    null,
                    "ingest_api_key is required for fm-module delivery test",
                    0L
            );
        }

        Map<String, Object> payload = request.test_payload() != null
                ? request.test_payload()
                : defaultProbePayload(config.getSourceKey());

        long start = System.currentTimeMillis();
        try {
            WebhookAcceptedResponse delivery = webhookService.deliver(config, ingestApiKey, payload);
            long latency = System.currentTimeMillis() - start;
            boolean success = "forwarded".equals(delivery.delivery());
            return new ProbeResponse(
                    success,
                    request.source_id(),
                    Instant.now(),
                    delivery.delivery(),
                    delivery.ingest_status(),
                    success ? null : "fm-module unavailable, message buffered",
                    latency
            );
        } catch (AdapterException ex) {
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
