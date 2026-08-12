package ru.wisla.fm.ingestion.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.wisla.fm.ingestion.adapter.in.web.IngestRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RawEventEnvelopeTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void roundTripsEventBatchEnvelope() throws Exception {
        Instant occurredAt = Instant.parse("2026-08-03T12:00:00Z");
        Instant producedAt = Instant.parse("2026-08-03T12:00:01Z");
        Instant receivedAt = Instant.parse("2026-08-03T12:00:00.500Z");
        UUID messageId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID sourceId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        IngestRequest body = new IngestRequest(
                false,
                List.of(new IngestRequest.IngestEventPayload(
                        "ext-1",
                        "Disk full",
                        "Disk usage above threshold",
                        "critical",
                        "new",
                        occurredAt,
                        "demo-server.wisla.local",
                        Map.of("k", "v"),
                        Map.of("raw", true))),
                "1.0.0",
                receivedAt);

        RawEventEnvelope envelope = new RawEventEnvelope(
                1, messageId, producedAt, sourceId, "demo", body);

        String json = objectMapper.writeValueAsString(envelope);
        JsonNode tree = objectMapper.readTree(json);

        assertThat(tree.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(tree.get("messageId").asText()).isEqualTo(messageId.toString());
        assertThat(tree.get("producedAt").asText()).isEqualTo(producedAt.toString());
        assertThat(tree.get("sourceId").asText()).isEqualTo(sourceId.toString());
        assertThat(tree.get("sourceKey").asText()).isEqualTo("demo");
        assertThat(tree.get("body").get("events").isArray()).isTrue();
        assertThat(tree.get("body").get("adapterVersion").asText()).isEqualTo("1.0.0");
        assertThat(json).doesNotContain("apiKey").doesNotContain("api_key");

        RawEventEnvelope restored = objectMapper.readValue(json, RawEventEnvelope.class);
        assertThat(restored).isEqualTo(envelope);
        assertThat(restored.body().events()).hasSize(1);
        assertThat(restored.body().events().getFirst().externalId()).isEqualTo("ext-1");
    }

    @Test
    void roundTripsHeartbeatEnvelope() throws Exception {
        Instant producedAt = Instant.parse("2026-08-03T13:00:00Z");
        Instant receivedAt = Instant.parse("2026-08-03T13:00:00Z");
        UUID messageId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        IngestRequest body = new IngestRequest(true, List.of(), "1.0.0-test", receivedAt);
        RawEventEnvelope envelope = new RawEventEnvelope(
                1, messageId, producedAt, sourceId, "demo", body);

        String json = objectMapper.writeValueAsString(envelope);
        RawEventEnvelope restored = objectMapper.readValue(json, RawEventEnvelope.class);

        assertThat(restored.body().heartbeat()).isTrue();
        assertThat(restored.body().events()).isEmpty();
        assertThat(restored).isEqualTo(envelope);
    }
}
