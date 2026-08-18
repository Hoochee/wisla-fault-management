package ru.wisla.fm.processing.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaEntity;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaRepository;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventControllerTest extends AbstractFmModuleTest {

  @Autowired private EventJpaRepository eventJpaRepository;

  private String adminToken;
  private String eventId;
  private String eventTitle;

  @BeforeEach
  void setUp() throws Exception {
    adminToken = obtainAdminToken();
    eventTitle = "DutyEvent-" + System.nanoTime();
    ingestEvent(eventTitle, "critical", "evt-" + System.nanoTime());
    eventId = fetchEventIdByTitle(eventTitle);
  }

  @Test
  void listEventsWithoutAuthReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/events")).andExpect(status().isUnauthorized());
  }

  @Test
  void listEventsByProductIdIncludesOwnCisAndExcludesOtherProducts() throws Exception {
    String suffix = UUID.randomUUID().toString();
    String fqdnA = "product-a-" + suffix + ".wisla.local";
    String fqdnB = "product-b-" + suffix + ".wisla.local";
    String ciA = createConfigurationItem(adminToken, fqdnA);
    String ciB = createConfigurationItem(adminToken, fqdnB);
    String productA = createProduct(adminToken, "prod-a-" + suffix.substring(0, 8));
    String productB = createProduct(adminToken, "prod-b-" + suffix.substring(0, 8));
    bindProductCis(adminToken, productA, ciA);
    bindProductCis(adminToken, productB, ciB);

    String titleA = "ProductA-Event-" + suffix;
    String titleB = "ProductB-Event-" + suffix;
    ingestEvent(titleA, "critical", "ext-a-" + suffix, fqdnA);
    ingestEvent(titleB, "critical", "ext-b-" + suffix, fqdnB);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/events")
                    .param("productId", productA)
                    .param("size", "500")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
    org.assertj.core.api.Assertions.assertThat(indexOfTitleOrMissing(items, titleA))
        .isGreaterThanOrEqualTo(0);
    org.assertj.core.api.Assertions.assertThat(indexOfTitleOrMissing(items, titleB)).isEqualTo(-1);
  }

  @Test
  void listEventsByEmptyOrUnknownProductIdReturnsEmptyPage() throws Exception {
    String emptyProductId =
        createProduct(adminToken, "empty-" + UUID.randomUUID().toString().substring(0, 8));
    String unknownProductId = UUID.randomUUID().toString();

    mockMvc
        .perform(
            get("/api/v1/events")
                .param("productId", emptyProductId)
                .param("size", "500")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(0));

    mockMvc
        .perform(
            get("/api/v1/events")
                .param("productId", unknownProductId)
                .param("size", "500")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void listEventsAndsProductIdWithSeverity() throws Exception {
    String suffix = UUID.randomUUID().toString();
    String fqdnA = "and-a-" + suffix + ".wisla.local";
    String fqdnB = "and-b-" + suffix + ".wisla.local";
    String ciA = createConfigurationItem(adminToken, fqdnA);
    String ciB = createConfigurationItem(adminToken, fqdnB);
    String productA = createProduct(adminToken, "and-a-" + suffix.substring(0, 8));
    String productB = createProduct(adminToken, "and-b-" + suffix.substring(0, 8));
    bindProductCis(adminToken, productA, ciA);
    bindProductCis(adminToken, productB, ciB);

    String majorTitle = "AndMajor-" + suffix;
    String criticalTitle = "AndCritical-" + suffix;
    String otherMajorTitle = "AndOtherMajor-" + suffix;
    ingestEvent(majorTitle, "major", "and-maj-" + suffix, fqdnA);
    ingestEvent(criticalTitle, "critical", "and-crit-" + suffix, fqdnA);
    ingestEvent(otherMajorTitle, "major", "and-other-" + suffix, fqdnB);

    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/events")
                    .param("productId", productA)
                    .param("severity", "major")
                    .param("size", "500")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
    org.assertj.core.api.Assertions.assertThat(indexOfTitleOrMissing(items, majorTitle))
        .isGreaterThanOrEqualTo(0);
    org.assertj.core.api.Assertions.assertThat(indexOfTitleOrMissing(items, criticalTitle))
        .isEqualTo(-1);
    org.assertj.core.api.Assertions.assertThat(indexOfTitleOrMissing(items, otherMajorTitle))
        .isEqualTo(-1);
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
        .andExpect(jsonPath("$.event.title").value(eventTitle))
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

  @Test
  void ackKeepsStatusAndWritesAuditColumns() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "ack"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.status").value("new"))
        .andExpect(jsonPath("$.event.closedAt").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.event.acknowledgedAt").exists())
        .andExpect(jsonPath("$.event.acknowledgedByUserId").exists())
        .andExpect(jsonPath("$.logEntry.action").value("ack"));

    MvcResult listed =
        mockMvc
            .perform(get("/api/v1/events").param("size", "500").header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    org.assertj.core.api.Assertions.assertThat(indexOfIdOrMissing(
            objectMapper.readTree(listed.getResponse().getContentAsString()).get("items"), eventId))
        .isGreaterThanOrEqualTo(0);
  }

  @Test
  void repeatAckUpdatesTimestamp() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "ack"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "ack"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.acknowledgedAt").exists())
        .andExpect(jsonPath("$.logEntry.action").value("ack"));
  }

  @Test
  void ackOnClosedOrArchivedReturns409() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "close"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "ack"))))
        .andExpect(status().isConflict());

    String archivedId = ingestNamedEvent("AckArchived-" + System.nanoTime());
    mockMvc
        .perform(
            patch("/api/v1/events/" + archivedId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "archived"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/events/" + archivedId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "ack"))))
        .andExpect(status().isConflict());
  }

  @Test
  void commentSucceedsAndBlankCommentReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("action", "comment", "comment", "looking into it"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.status").value("new"))
        .andExpect(jsonPath("$.logEntry.action").value("comment"));

    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "comment"))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "comment", "comment", "   "))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void assignColleagueKeepsStatusWithoutInProgress() throws Exception {
    String colleagueId = createActiveUser("assign-col");

    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("action", "assign", "assignedUserId", colleagueId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.status").value("new"))
        .andExpect(jsonPath("$.event.assignedUserId").value(colleagueId))
        .andExpect(jsonPath("$.event.assignedUserName").value("Assign Colleague"))
        .andExpect(jsonPath("$.logEntry.action").value("assign"));
  }

  @Test
  void takeStillSetsInProgressAfterAssignApi() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "take"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.status").value("in_progress"))
        .andExpect(jsonPath("$.event.assignedUserId").exists())
        .andExpect(jsonPath("$.event.takenAt").exists())
        .andExpect(jsonPath("$.logEntry.action").value("take"));
  }

  @Test
  void assignWithoutUserIdUnknownAndInactiveAreRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "assign"))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("action", "assign", "assignedUserId", "00000000-0000-0000-0000-000000000099"))))
        .andExpect(status().isNotFound());

    String inactiveId = createActiveUser("assign-idle");
    mockMvc
        .perform(
            patch("/api/v1/admin/users/" + inactiveId)
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("active", false))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("action", "assign", "assignedUserId", inactiveId))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void silenceSetsUntilAndRejectsBadMinutesAndClosed() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("action", "silence", "silenceMinutes", 30))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.status").value("new"))
        .andExpect(jsonPath("$.event.severity").value("critical"))
        .andExpect(jsonPath("$.event.silencedUntil").exists())
        .andExpect(jsonPath("$.logEntry.action").value("silence"));

    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "silence"))))
        .andExpect(status().isBadRequest());

    Map<String, Object> zeroMinutes = new HashMap<>();
    zeroMinutes.put("action", "silence");
    zeroMinutes.put("silenceMinutes", 0);
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(zeroMinutes)))
        .andExpect(status().isBadRequest());

    String closedId = ingestNamedEvent("SilenceClosed-" + System.nanoTime());
    mockMvc
        .perform(
            post("/api/v1/events/" + closedId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "close"))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/events/" + closedId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("action", "silence", "silenceMinutes", 15))))
        .andExpect(status().isConflict());
  }

  @Test
  void takeAndCloseRemainAllowedOnSilencedEvent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("action", "silence", "silenceMinutes", 15))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "take"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.status").value("in_progress"));

    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "close"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.status").value("closed"));
  }

  @Test
  void listHidesSilencedEventsUnlessRequestedAndDetailAlwaysReturns() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("action", "silence", "silenceMinutes", 60))))
        .andExpect(status().isOk());

    MvcResult hidden =
        mockMvc
            .perform(
                get("/api/v1/events")
                    .param("size", "500")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    org.assertj.core.api.Assertions.assertThat(indexOfIdOrMissing(
            objectMapper.readTree(hidden.getResponse().getContentAsString()).get("items"), eventId))
        .isEqualTo(-1);

    MvcResult explicitFalse =
        mockMvc
            .perform(
                get("/api/v1/events")
                    .param("includeSilenced", "false")
                    .param("size", "500")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    org.assertj.core.api.Assertions.assertThat(indexOfIdOrMissing(
            objectMapper.readTree(explicitFalse.getResponse().getContentAsString()).get("items"), eventId))
        .isEqualTo(-1);

    mockMvc
        .perform(
            get("/api/v1/events")
                .param("includeSilenced", "true")
                .param("size", "500")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.id=='" + eventId + "')].silencedUntil").exists());

    mockMvc
        .perform(get("/api/v1/events/" + eventId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.id").value(eventId))
        .andExpect(jsonPath("$.event.silencedUntil").exists());
  }

  @Test
  void expiredSilenceReturnsToTheActiveList() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/events/" + eventId + "/actions")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("action", "silence", "silenceMinutes", 60))))
        .andExpect(status().isOk());

    EventJpaEntity entity = eventJpaRepository.findById(UUID.fromString(eventId)).orElseThrow();
    entity.setSilencedUntil(Instant.now().minusSeconds(60));
    eventJpaRepository.saveAndFlush(entity);

    MvcResult listed =
        mockMvc
            .perform(
                get("/api/v1/events")
                    .param("size", "500")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    org.assertj.core.api.Assertions.assertThat(indexOfIdOrMissing(
            objectMapper.readTree(listed.getResponse().getContentAsString()).get("items"), eventId))
        .isGreaterThanOrEqualTo(0);
  }

  private String ingestNamedEvent(String title) throws Exception {
    ingestEvent(title, "critical", "evt-" + System.nanoTime());
    return fetchEventIdByTitle(title);
  }

  private String createActiveUser(String loginPrefix) throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String login = loginPrefix + "-" + suffix;
    String roleId = firstRoleId(adminToken);
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/users")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "login",
                                login,
                                "fullName",
                                "Assign Colleague",
                                "email",
                                login + "@wisla.local",
                                "password",
                                "secret123",
                                "roleIds",
                                List.of(roleId)))))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
  }

  private String firstRoleId(String token) throws Exception {
    MvcResult roles =
        mockMvc
            .perform(get("/api/v1/admin/roles").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(roles.getResponse().getContentAsString()).get(0).get("id").asText();
  }

  private int indexOfIdOrMissing(JsonNode items, String id) {
    for (int i = 0; i < items.size(); i++) {
      if (id.equals(items.get(i).get("id").asText())) {
        return i;
      }
    }
    return -1;
  }

  private void ingestEvent(String title, String severity, String externalId) throws Exception {
    ingestEvent(title, severity, externalId, "demo-server.wisla.local");
  }

  private void ingestEvent(String title, String severity, String externalId, String nodeFqdn)
      throws Exception {
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
                    nodeFqdn)));
    mockMvc
        .perform(
            post("/api/v1/ingest")
                .header("X-Api-Key", DEMO_SOURCE_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isAccepted());
  }

  private String createProduct(String token, String code) throws Exception {
    Map<String, Object> create =
        Map.of("name", "Product " + code, "code", code, "tenant", "moscow", "site", "dc1");
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/products")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
  }

  private String createConfigurationItem(String token, String fqdn) throws Exception {
    Map<String, Object> create =
        Map.of("fqdn", fqdn, "ciType", "node", "system", "Test System");
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/configuration-items")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
  }

  private void bindProductCis(String token, String productId, String ciId) throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ciIds", List.of(ciId)))))
        .andExpect(status().isOk());
  }

  private int indexOfTitleOrMissing(JsonNode items, String title) {
    for (int i = 0; i < items.size(); i++) {
      if (title.equals(items.get(i).get("title").asText())) {
        return i;
      }
    }
    return -1;
  }

  private int indexOfTitle(JsonNode items, String title) {
    for (int i = 0; i < items.size(); i++) {
      if (title.equals(items.get(i).get("title").asText())) {
        return i;
      }
    }
    throw new AssertionError("Event not found: " + title);
  }

  private String fetchEventIdByTitle(String title) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/events")
                    .param("includeSilenced", "true")
                    .param("size", "500")
                    .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
    return items.get(indexOfTitle(items, title)).get("id").asText();
  }
}
