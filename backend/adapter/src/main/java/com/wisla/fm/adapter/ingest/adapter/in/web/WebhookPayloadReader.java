package com.wisla.fm.adapter.ingest.adapter.in.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisla.fm.adapter.ingest.domain.IngestRejection;
import com.wisla.fm.adapter.ingest.infrastructure.config.AdapterProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Turns a raw webhook request body into the payload map the use case works on. Owns the two
 * transport-level refusals that used to live inside the use case: the {@code max-payload-bytes}
 * limit and JSON parsing.
 */
@Component
public class WebhookPayloadReader {

    private final ObjectMapper objectMapper;
    private final int maxPayloadBytes;

    public WebhookPayloadReader(ObjectMapper objectMapper, AdapterProperties properties) {
        this.objectMapper = objectMapper;
        this.maxPayloadBytes = properties.maxPayloadBytes();
    }

    public Map<String, Object> read(byte[] rawBody) {
        if (rawBody.length > maxPayloadBytes) {
            throw new IngestRejection(
                    "payload_too_large",
                    "Payload exceeds maximum allowed size",
                    413
            );
        }
        try {
            return objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IngestRejection("invalid_json", "Request body must be valid JSON", 400);
        }
    }
}
