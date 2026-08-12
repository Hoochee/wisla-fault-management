package com.wisla.fm.adapter.ingest.adapter.out.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RawEventEnvelopeCodecTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void serializeDeserializeRoundTripPreservesContractFields() throws Exception {
        UUID sourceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("events", List.of(Map.of(
                "externalId", "evt-1",
                "title", "Disk full",
                "severity", "major",
                "occurredAt", "2026-06-23T10:00:00Z"
        )));
        body.put("adapterVersion", "1.0.0");
        body.put("receivedAt", "2026-06-23T10:00:01Z");

        RawEventEnvelope original = new RawEventEnvelope(
                RawEventEnvelope.CURRENT_SCHEMA_VERSION,
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                Instant.parse("2026-06-23T10:00:02Z"),
                sourceId,
                "zabbix-prod-01",
                body
        );

        String json = RawEventEnvelopeCodec.serialize(objectMapper, original);
        RawEventEnvelope restored = RawEventEnvelopeCodec.deserialize(objectMapper, json);

        assertThat(restored.schemaVersion()).isEqualTo(1);
        assertThat(restored.messageId()).isEqualTo(original.messageId());
        assertThat(restored.producedAt()).isEqualTo(original.producedAt());
        assertThat(restored.sourceId()).isEqualTo(sourceId);
        assertThat(restored.sourceKey()).isEqualTo("zabbix-prod-01");
        assertThat(restored.body()).containsEntry("adapterVersion", "1.0.0");
        assertThat(restored.body()).containsKey("events");
        assertThat(json).doesNotContain("apiKey");
        assertThat(json).doesNotContain("api_key");
        assertThat(json).doesNotContain("X-Api-Key");
    }

    @Test
    void heartbeatBodyRoundTrip() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("heartbeat", true);
        body.put("adapterVersion", "1.0.0");
        body.put("receivedAt", Instant.parse("2026-06-23T10:00:00Z").toString());

        RawEventEnvelope envelope = RawEventEnvelopeCodec.create(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "probe-source",
                body
        );

        RawEventEnvelope restored = RawEventEnvelopeCodec.deserialize(
                objectMapper,
                RawEventEnvelopeCodec.serialize(objectMapper, envelope)
        );

        assertThat(restored.body()).containsEntry("heartbeat", true);
        assertThat(restored.body()).doesNotContainKey("events");
    }

    @Test
    void deserializeRejectsInvalidJson() {
        assertThatThrownBy(() -> RawEventEnvelopeCodec.deserialize(objectMapper, "not-json"))
                .isInstanceOf(Exception.class);
    }
}
