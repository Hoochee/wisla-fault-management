package ru.wisla.fm.ingestion.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestControllerTest extends AbstractFmModuleTest {

  @Test
  void ingestWithoutApiKeyReturns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleIngestRequest())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void ingestWithValidSourceKeyAcceptsBatch() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/ingest")
                .header("X-Api-Key", DEMO_SOURCE_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sampleIngestRequest())))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.accepted").value(1))
        .andExpect(jsonPath("$.rejected").value(0))
        .andExpect(jsonPath("$.rawEventIds").isArray());
  }

  @Test
  void ingestHeartbeatAcknowledges() throws Exception {
    Map<String, Object> body =
        Map.of("heartbeat", true, "adapterVersion", "1.0.0-test", "events", List.of());
    mockMvc
        .perform(
            post("/api/v1/ingest")
                .header("X-Api-Key", DEMO_SOURCE_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.heartbeatAck").value(true));
  }

  private Map<String, Object> sampleIngestRequest() {
    return Map.of(
        "events",
        List.of(
            Map.of(
                "externalId",
                "ext-" + System.nanoTime(),
                "title",
                "Test disk full",
                "description",
                "Disk usage above threshold",
                "severity",
                "critical",
                "occurredAt",
                Instant.now().toString(),
                "nodeFqdn",
                "demo-server.wisla.local")));
  }
}
