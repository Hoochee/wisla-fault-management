package com.wisla.fm.adapter.ingest.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RawEventEnvelopeCodec {

    private RawEventEnvelopeCodec() {
    }

    public static RawEventEnvelope create(UUID sourceId, String sourceKey, Map<String, Object> ingestBody) {
        return new RawEventEnvelope(
                RawEventEnvelope.CURRENT_SCHEMA_VERSION,
                UUID.randomUUID(),
                Instant.now(),
                sourceId,
                sourceKey,
                ingestBody
        );
    }

    public static String serialize(ObjectMapper objectMapper, RawEventEnvelope envelope) throws JsonProcessingException {
        return objectMapper.writeValueAsString(toJsonMap(envelope));
    }

    public static RawEventEnvelope deserialize(ObjectMapper objectMapper, String json) throws JsonProcessingException {
        Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
        int schemaVersion = ((Number) root.get("schemaVersion")).intValue();
        UUID messageId = UUID.fromString(String.valueOf(root.get("messageId")));
        Instant producedAt = Instant.parse(String.valueOf(root.get("producedAt")));
        UUID sourceId = UUID.fromString(String.valueOf(root.get("sourceId")));
        String sourceKey = String.valueOf(root.get("sourceKey"));
        Map<String, Object> body = objectMapper.convertValue(root.get("body"), new TypeReference<>() {});
        return new RawEventEnvelope(schemaVersion, messageId, producedAt, sourceId, sourceKey, body);
    }

    private static Map<String, Object> toJsonMap(RawEventEnvelope envelope) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", envelope.schemaVersion());
        map.put("messageId", envelope.messageId().toString());
        map.put("producedAt", envelope.producedAt().toString());
        map.put("sourceId", envelope.sourceId().toString());
        map.put("sourceKey", envelope.sourceKey());
        map.put("body", envelope.body());
        return map;
    }
}
