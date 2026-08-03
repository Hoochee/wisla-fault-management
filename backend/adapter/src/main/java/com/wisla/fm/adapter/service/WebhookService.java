package com.wisla.fm.adapter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisla.fm.adapter.config.AdapterProperties;
import com.wisla.fm.adapter.kafka.RawEventPublisher;
import com.wisla.fm.adapter.persistence.entity.BufferedMessage;
import com.wisla.fm.adapter.persistence.entity.SourceConfigSnapshot;
import com.wisla.fm.adapter.web.dto.WebhookAcceptedResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WebhookService {

    private final SourceConfigService sourceConfigService;
    private final FilterService filterService;
    private final RawEventPublisher rawEventPublisher;
    private final IngestPayloadMapper ingestPayloadMapper;
    private final BufferService bufferService;
    private final PasswordEncoder passwordEncoder;
    private final AdapterProperties properties;
    private final ObjectMapper objectMapper;

    public WebhookService(
            SourceConfigService sourceConfigService,
            FilterService filterService,
            RawEventPublisher rawEventPublisher,
            IngestPayloadMapper ingestPayloadMapper,
            BufferService bufferService,
            PasswordEncoder passwordEncoder,
            AdapterProperties properties,
            ObjectMapper objectMapper
    ) {
        this.sourceConfigService = sourceConfigService;
        this.filterService = filterService;
        this.rawEventPublisher = rawEventPublisher;
        this.ingestPayloadMapper = ingestPayloadMapper;
        this.bufferService = bufferService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public WebhookAcceptedResponse receive(
            String sourceKey,
            String headerApiKey,
            String queryApiKey,
            byte[] rawBody
    ) {
        validatePayloadSize(rawBody);

        Map<String, Object> payload = parsePayload(rawBody);
        SourceConfigSnapshot config = sourceConfigService.requireBySourceKey(sourceKey);

        String ingestApiKey = validateApiKey(sourceKey, headerApiKey, queryApiKey, config);
        ensureSourceActive(config);

        if (filterService.shouldDrop(config.getFilterRules(), payload)) {
            throw new AdapterException("filtered", "Event rejected by pre-filter rules", 400);
        }

        return deliver(config, ingestApiKey, payload);
    }

    WebhookAcceptedResponse deliver(SourceConfigSnapshot config, String ingestApiKey, Map<String, Object> payload) {
        Map<String, Object> ingestBody = ingestPayloadMapper.toIngestRequest(payload);
        RawEventPublisher.PublishResult result = rawEventPublisher.publish(
                config.getSourceId(),
                config.getSourceKey(),
                ingestBody
        );

        if (result.success()) {
            return new WebhookAcceptedResponse(true, "forwarded", null, null);
        }

        if (!result.retryable()) {
            throw new AdapterException(
                    "ingest_rejected",
                    result.error() != null ? result.error() : "Kafka publish rejected",
                    502
            );
        }

        BufferedMessage buffered = bufferService.buffer(config.getSourceId(), ingestApiKey, payload);
        return new WebhookAcceptedResponse(true, "buffered", buffered.getId(), null);
    }

    private void validatePayloadSize(byte[] rawBody) {
        if (rawBody.length > properties.maxPayloadBytes()) {
            throw new AdapterException(
                    "payload_too_large",
                    "Payload exceeds maximum allowed size",
                    413
            );
        }
    }

    private Map<String, Object> parsePayload(byte[] rawBody) {
        try {
            return objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new AdapterException("invalid_json", "Request body must be valid JSON", 400);
        }
    }

    private String validateApiKey(
            String sourceKey,
            String headerApiKey,
            String queryApiKey,
            SourceConfigSnapshot config
    ) {
        if (headerApiKey != null && queryApiKey != null && !headerApiKey.equals(queryApiKey)) {
            throw new AdapterException(
                    "invalid_source_key",
                    "Header and query API keys do not match",
                    401
            );
        }

        String providedKey = headerApiKey != null ? headerApiKey : queryApiKey;
        if (providedKey == null || providedKey.isBlank()) {
            throw new AdapterException(
                    "missing_api_key",
                    "API key is required via X-Source-Key header or sourceKey query parameter",
                    401
            );
        }

        if (!passwordEncoder.matches(providedKey, config.getApiKeyHash())) {
            throw new AdapterException(
                    "invalid_source_key",
                    "API key does not match source configuration",
                    401
            );
        }

        return providedKey;
    }

    private void ensureSourceActive(SourceConfigSnapshot config) {
        if (config.isBlocked()) {
            throw new AdapterException(
                    "source_blocked",
                    "Source is blocked due to event storm",
                    403
            );
        }
    }
}
