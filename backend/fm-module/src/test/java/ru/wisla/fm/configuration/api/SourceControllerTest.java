package ru.wisla.fm.configuration.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MvcResult;
import ru.wisla.fm.adapters.client.SimulatorHealthResponse;
import ru.wisla.fm.adapters.client.SimulatorTickResponse;
import ru.wisla.fm.adapters.client.ZabbixSimulatorClient;
import ru.wisla.fm.support.AbstractFmModuleTest;
import ru.wisla.fm.testsupport.TestZabbixSimulatorClient;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockRestServiceServer
@Import(SourceControllerTest.SimulatorStubConfig.class)
class SourceControllerTest extends AbstractFmModuleTest {

  private static final String ADAPTER_BASE = "http://localhost:18081";

  @Autowired private MockRestServiceServer mockServer;
  @Autowired private TestZabbixSimulatorClient testZabbixSimulatorClient;

  @BeforeEach
  void resetMocks() {
    mockServer.reset();
    testZabbixSimulatorClient.setHealthResponse(
        new SimulatorHealthResponse("ok", true, null, null, 0));
    testZabbixSimulatorClient.setTickResponse(
        new SimulatorTickResponse("problem", "cpu-high", true, 202, null, "adapter accepted"));
  }

  @Test
  void listSourcesWithoutAuthReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/sources")).andExpect(status().isUnauthorized());
  }

  @Test
  void listSourcesReturnsSeededDemoSource() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/sources").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[0].name").exists())
        .andExpect(jsonPath("$[0].status").exists());
  }

  @Test
  void createAndGetSource() throws Exception {
    expectAdapterSync();

    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "Test SNMP Source",
            "type",
            "snmp",
            "protocol",
            "SNMP",
            "endpoint",
            "udp://127.0.0.1:161",
            "status",
            "inactive");

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/sources")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test SNMP Source"))
            .andExpect(jsonPath("$.apiKey").isNotEmpty())
            .andReturn();

    String sourceId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(get("/api/v1/sources/" + sourceId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source.name").value("Test SNMP Source"))
        .andExpect(jsonPath("$.webhookUrl").value(containsString("/webhook/")));
  }

  @Test
  void patchAndDeleteSource() throws Exception {
    expectAdapterSync();
    expectAdapterSync();

    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "Patchable Source",
            "type",
            "push_rest",
            "protocol",
            "HTTPS/REST",
            "endpoint",
            "http://localhost:9999/webhook");

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/sources")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
    String sourceId = body.get("id").asText();

    mockMvc
        .perform(
            patch("/api/v1/sources/" + sourceId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "active"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("active"));

    mockMvc
        .perform(delete("/api/v1/sources/" + sourceId).header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/v1/sources/" + sourceId).header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
  }

  @Test
  void testSourceUsesAdapterProbe() throws Exception {
    expectAdapterSync();
    expectAdapterSync();
    mockServer
        .expect(method(HttpMethod.POST))
        .andExpect(requestTo(ADAPTER_BASE + "/internal/probe"))
        .andRespond(
            withSuccess(
                """
                {
                  "success": true,
                  "source_id": "00000000-0000-0000-0000-000000000001",
                  "probed_at": "2026-06-26T12:00:00Z",
                  "delivery": "forwarded",
                  "ingest_status": 202,
                  "latency_ms": 42
                }
                """,
                MediaType.APPLICATION_JSON));

    String token = obtainAdminToken();
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/sources")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "name",
                                "Probe Source",
                                "type",
                                "push_rest",
                                "protocol",
                                "HTTPS/REST",
                                "endpoint",
                                "http://localhost/webhook"))))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
    String sourceId = body.get("id").asText();
    String apiKey = body.get("apiKey").asText();

    mockMvc
        .perform(
            post("/api/v1/sources/" + sourceId + "/test")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ingestApiKey", apiKey))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.delivery").value("forwarded"))
        .andExpect(jsonPath("$.latencyMs").value(42));
  }

  @Test
  void bindSimulatorAndSendTestEvent() throws Exception {
    expectAdapterSync();
    expectAdapterSync();
    expectAdapterSync();
    expectAdapterSync();

    String token = obtainAdminToken();
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/sources")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "name",
                                "Simulator Source",
                                "type",
                                "push_rest",
                                "protocol",
                                "HTTPS/REST",
                                "endpoint",
                                "http://localhost/webhook"))))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode createBody = objectMapper.readTree(created.getResponse().getContentAsString());
    String sourceId = createBody.get("id").asText();
    String apiKey = createBody.get("apiKey").asText();

    MvcResult detail =
        mockMvc
            .perform(get("/api/v1/sources/" + sourceId).header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn();
    String webhookUrl =
        objectMapper.readTree(detail.getResponse().getContentAsString()).get("webhookUrl").asText();
    String webhookKey = webhookUrl.substring(webhookUrl.lastIndexOf('/') + 1);

    testZabbixSimulatorClient.setHealthResponse(
        new SimulatorHealthResponse("ok", true, webhookKey, webhookUrl, 1));

    mockMvc
        .perform(
            post("/api/v1/sources/" + sourceId + "/bind-simulator")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ingestApiKey", apiKey))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.sourceWebhookKey").value(webhookKey));

    mockMvc
        .perform(
            get("/api/v1/sources/" + sourceId + "/simulator-status")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reachable").value(true))
        .andExpect(jsonPath("$.bound").value(true));

    mockMvc
        .perform(
            post("/api/v1/sources/" + sourceId + "/send-test-event")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kind").value("problem"))
        .andExpect(jsonPath("$.delivered").value(true));
  }

  @Test
  void setSimulatorControl() throws Exception {
    expectAdapterSync();

    String token = obtainAdminToken();
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/sources")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "name",
                                "Control Source",
                                "type",
                                "push_rest",
                                "protocol",
                                "HTTPS/REST",
                                "endpoint",
                                "http://localhost/webhook"))))
            .andExpect(status().isCreated())
            .andReturn();

    String sourceId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/sources/" + sourceId + "/simulator-control")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.message").value("auto-tick disabled"));

    mockMvc
        .perform(
            post("/api/v1/sources/" + sourceId + "/simulator-control")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.message").value("auto-tick enabled"));
  }

  private void expectAdapterSync() {
    mockServer
        .expect(method(HttpMethod.POST))
        .andExpect(requestTo(ADAPTER_BASE + "/internal/config/sync"))
        .andRespond(withSuccess());
  }

  @TestConfiguration
  static class SimulatorStubConfig {

    @Bean
    @Primary
    TestZabbixSimulatorClient testZabbixSimulatorClient() {
      return new TestZabbixSimulatorClient();
    }
  }
}
