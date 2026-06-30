package ru.wisla.fm.adapters.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import ru.wisla.fm.configuration.domain.EventSourceEntity;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockRestServiceServer
class AdapterRuntimeControllerTest extends AbstractFmModuleTest {

  private static final String ADAPTER_HEALTH_URL = "http://localhost:18081/health";

  @Autowired private MockRestServiceServer mockServer;
  @Autowired private EventSourceRepository eventSourceRepository;

  @BeforeEach
  void resetMockServer() {
    mockServer.reset();
  }

  @Test
  void getRuntimeWithoutAuthReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/adapters/runtime")).andExpect(status().isUnauthorized());
  }

  @Test
  void getRuntimeReturnsServiceHealthAndSourceStatuses() throws Exception {
    mockServer
        .expect(requestTo(ADAPTER_HEALTH_URL))
        .andRespond(
            withSuccess(
                """
                {"status":"ok","version":"1.0.0","database":"up","fm_module":"reachable","buffered_count":3}
                """,
                MediaType.APPLICATION_JSON));

    EventSourceEntity activeRecent = requireSourceByName("Demo Push REST");
    activeRecent.setLastSuccessAt(Instant.now());
    eventSourceRepository.save(activeRecent);

    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/adapters/runtime").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service.status").value("ok"))
        .andExpect(jsonPath("$.service.version").value("1.0.0"))
        .andExpect(jsonPath("$.service.database").value("up"))
        .andExpect(jsonPath("$.service.fmModule").value("reachable"))
        .andExpect(jsonPath("$.service.bufferedMessages").value(3))
        .andExpect(jsonPath("$.service.baseUrl").value("http://localhost:18081"))
        .andExpect(jsonPath("$.sources[?(@.name=='Demo Push REST')].adapterRuntimeStatus")
            .value("running"))
        .andExpect(jsonPath("$.sources[?(@.name=='Zabbix Main (simulator)')].adapterRuntimeStatus")
            .value("idle"));
  }

  @Test
  void getRuntimeWhenAdapterUnreachableMarksActiveSourcesUnreachable() throws Exception {
    mockServer
        .expect(requestTo(ADAPTER_HEALTH_URL))
        .andRespond(withException(new java.net.ConnectException("Connection refused")));

    String token = obtainOperatorToken();
    mockMvc
        .perform(get("/api/v1/adapters/runtime").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service.status").value("down"))
        .andExpect(jsonPath("$.sources[0].adapterRuntimeStatus").value("unreachable"))
        .andExpect(jsonPath("$.sources[1].adapterRuntimeStatus").value("unreachable"));
  }

  @Test
  void getRuntimeMarksActiveIdleWhenNoRecentSuccess() throws Exception {
    mockServer
        .expect(requestTo(ADAPTER_HEALTH_URL))
        .andRespond(
            withSuccess(
                """
                {"status":"ok","version":"1.0.0","database":"up","fm_module":"reachable","buffered_count":0}
                """,
                MediaType.APPLICATION_JSON));

    EventSourceEntity activeStale = requireSourceByName("Zabbix Main (simulator)");
    activeStale.setLastSuccessAt(Instant.now().minusSeconds(3600));
    eventSourceRepository.save(activeStale);

    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/adapters/runtime").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sources[?(@.name=='Zabbix Main (simulator)')].adapterRuntimeStatus")
            .value("idle"));
  }

  @Test
  void getRuntimeMarksBlockedSourceDegraded() throws Exception {
    mockServer
        .expect(requestTo(ADAPTER_HEALTH_URL))
        .andRespond(
            withSuccess(
                """
                {"status":"ok","version":"1.0.0","database":"up","fm_module":"reachable","buffered_count":0}
                """,
                MediaType.APPLICATION_JSON));

    EventSourceEntity blocked = new EventSourceEntity();
    blocked.setName("Blocked Source");
    blocked.setType("push_rest");
    blocked.setProtocol("HTTPS/REST");
    blocked.setEndpoint("http://localhost:8081/webhook/blocked");
    blocked.setApiKeyHash("hash");
    blocked.setApiKeyPrefix("blocked-****");
    blocked.setStatus("blocked");
    blocked.setWebhookPathKey("blocked-" + UUID.randomUUID());
    eventSourceRepository.save(blocked);

    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/adapters/runtime").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sources[?(@.name=='Blocked Source')].adapterRuntimeStatus")
            .value("degraded"));
  }

  private EventSourceEntity requireSourceByName(String name) {
    return eventSourceRepository.findAll().stream()
        .filter(source -> name.equals(source.getName()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Missing seeded source: " + name));
  }
}
