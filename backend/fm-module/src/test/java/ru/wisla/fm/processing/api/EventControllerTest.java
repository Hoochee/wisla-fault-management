package ru.wisla.fm.processing.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventControllerTest extends AbstractFmModuleTest {

  private String adminToken;
  private String eventId;

  @BeforeEach
  void setUp() throws Exception {
    adminToken = obtainAdminToken();
    ingestSampleEvent();
    eventId = fetchFirstEventId();
  }

  @Test
  void listEventsWithoutAuthReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/events")).andExpect(status().isUnauthorized());
  }

  @Test
  void listEventsReturnsPage() throws Exception {
    mockMvc
        .perform(get("/api/v1/events").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.page.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
  }

  @Test
  void getEventByIdReturnsDetail() throws Exception {
    mockMvc
        .perform(get("/api/v1/events/" + eventId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.id").value(eventId))
        .andExpect(jsonPath("$.event.title").value("Test disk full"))
        .andExpect(jsonPath("$.actionLogs").isArray());
  }

  @Test
  void getUnknownEventReturns404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/events/00000000-0000-0000-0000-000000000099")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
  }

  @Test
  void patchEventUpdatesSeverity() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/events/" + eventId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("severity", "major"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.severity").value("major"));
  }

  @Test
  void takeActionUpdatesStatus() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "take"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.status").value("in_progress"))
        .andExpect(jsonPath("$.logEntry.action").value("take"));
  }

  @Test
  void listEventsSortByRepeatCountDesc() throws Exception {
    String title = "SortRepeat-" + System.nanoTime();
    ingestEvent(title, "warning", "repeat-ext-" + System.nanoTime());
    for (int i = 0; i < 2; i++) {
      ingestEvent(title, "warning", "repeat-ext-" + System.nanoTime());
    }

    String lowRepeatTitle = "SortRepeatLow-" + System.nanoTime();
    ingestEvent(lowRepeatTitle, "warning", "repeat-low-" + System.nanoTime());

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/events")
                    .param("sort", "repeatCount,desc")
                    .param("size", "500")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
    int highIdx = indexOfTitle(items, title);
    int lowIdx = indexOfTitle(items, lowRepeatTitle);
    org.assertj.core.api.Assertions.assertThat(highIdx).isLessThan(lowIdx);
  }

  @Test
  void listEventsSortBySeverityAsc() throws Exception {
    String prefix = "SortSev-" + System.nanoTime();
    ingestEvent(prefix + "-warning", "warning", prefix + "-w");
    ingestEvent(prefix + "-fatal", "fatal", prefix + "-f");
    ingestEvent(prefix + "-critical", "critical", prefix + "-c");

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/events")
                    .param("sort", "severity,asc")
                    .param("size", "500")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
    int fatalIdx = indexOfTitle(items, prefix + "-fatal");
    int criticalIdx = indexOfTitle(items, prefix + "-critical");
    int warningIdx = indexOfTitle(items, prefix + "-warning");
    org.assertj.core.api.Assertions.assertThat(fatalIdx).isLessThan(criticalIdx);
    org.assertj.core.api.Assertions.assertThat(criticalIdx).isLessThan(warningIdx);
  }

  @Test
  void listEventsSortByLastRepeatAtDesc() throws Exception {
    String title = "SortLastRepeat-" + System.nanoTime();
    ingestEvent(title, "warning", "lr-ext-" + System.nanoTime());
    ingestEvent(title, "warning", "lr-ext-" + (System.nanoTime() + 1));
    ingestEvent(title, "warning", "lr-ext-" + (System.nanoTime() + 2));

    String noRepeatTitle = "SortLastRepeatNone-" + System.nanoTime();
    ingestEvent(noRepeatTitle, "warning", "lr-none-" + System.nanoTime());

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/events")
                    .param("sort", "lastRepeatAt,desc")
                    .param("size", "500")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
    int withRepeatIdx = indexOfTitle(items, title);
    int noRepeatIdx = indexOfTitle(items, noRepeatTitle);
    org.assertj.core.api.Assertions.assertThat(withRepeatIdx).isLessThan(noRepeatIdx);
    org.assertj.core.api.Assertions.assertThat(items.get(withRepeatIdx).get("repeatCount").asInt())
        .isGreaterThanOrEqualTo(3);
    org.assertj.core.api.Assertions.assertThat(items.get(withRepeatIdx).has("lastRepeatAt")).isTrue();
  }

  @Test
  void listEventsInvalidSortFieldReturns400() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/events")
                .param("sort", "unknownField,asc")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("bad_request"));
  }

  @Test
  void closeActionClosesEvent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "take"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "close"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.status").value("closed"))
        .andExpect(jsonPath("$.logEntry.action").value("close"));
  }

  private void ingestSampleEvent() throws Exception {
    ingestEvent("Test disk full", "critical", "evt-" + System.nanoTime());
  }

  private void ingestEvent(String title, String severity, String externalId) throws Exception {
    Map<String, Object> body =
        Map.of(
            "events",
            List.of(
                Map.of(
                    "externalId",
                    externalId,
                    "title",
                    title,
                    "description",
                    "Disk usage above threshold",
                    "severity",
                    severity,
                    "occurredAt",
                    Instant.now().toString(),
                    "nodeFqdn",
                    "demo-server.wisla.local")));
    mockMvc
        .perform(
            post("/api/v1/ingest")
                .header("X-Api-Key", DEMO_SOURCE_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isAccepted());
  }

  private int indexOfTitle(JsonNode items, String title) {
    for (int i = 0; i < items.size(); i++) {
      if (title.equals(items.get(i).get("title").asText())) {
        return i;
      }
    }
    throw new AssertionError("Event not found: " + title);
  }

  private String fetchFirstEventId() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/events").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    for (JsonNode item : body.get("items")) {
      if ("Test disk full".equals(item.get("title").asText())) {
        return item.get("id").asText();
      }
    }
    return body.get("items").get(0).get("id").asText();
  }
}
