package ru.wisla.fm.rules.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import ru.wisla.fm.rules.domain.ProcessingRuleEntity;
import ru.wisla.fm.rules.persistence.ProcessingRuleRepository;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.util.Map;
import java.util.List;

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuleControllerTest extends AbstractFmModuleTest {

  @Autowired private ProcessingRuleRepository processingRuleRepository;

  @Test
  void listRulesWithoutAuthReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/rules")).andExpect(status().isUnauthorized());
  }

  @Test
  void listRulesReturnsSeededRules() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/rules").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
        .andExpect(jsonPath("$[0].ruleType").exists())
        .andExpect(jsonPath("$[0].enabled").isBoolean());
  }

  @Test
  void getRuleByIdReturnsDetail() throws Exception {
    String token = obtainAdminToken();
    MvcResult list =
        mockMvc
            .perform(get("/api/v1/rules").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode rules = objectMapper.readTree(list.getResponse().getContentAsString());
    String ruleId = rules.get(0).get("id").asText();

    mockMvc
        .perform(get("/api/v1/rules/" + ruleId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ruleId))
        .andExpect(jsonPath("$.name").exists());
  }

  @Test
  void getUnknownRuleReturns404() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(
            get("/api/v1/rules/00000000-0000-0000-0000-000000000099")
                .header("Authorization", bearer(token)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
  }

  @Test
  void createRuleReturnsDraftRule() throws Exception {
    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "New correlation rule",
            "ruleType",
            "correlation",
            "triggerType",
            "Событие потока",
            "description",
            "Test rule creation");

    mockMvc
        .perform(
            post("/api/v1/rules")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("New correlation rule"))
        .andExpect(jsonPath("$.ruleType").value("correlation"))
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.approvalStatus").value("draft"));
  }

  @Test
  void patchRuleUpdatesMetadata() throws Exception {
    String token = obtainAdminToken();
    MvcResult list =
        mockMvc
            .perform(get("/api/v1/rules").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode rules = objectMapper.readTree(list.getResponse().getContentAsString());
    String ruleId = rules.get(0).get("id").asText();

    mockMvc
        .perform(
            patch("/api/v1/rules/" + ruleId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("description", "Updated via patch"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ruleId))
        .andExpect(jsonPath("$.description").value("Updated via patch"));
  }

  @Test
  void createRuleWithoutStreamTriggerReturns400() throws Exception {
    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "Invalid rule",
            "ruleType",
            "dedup",
            "triggerType",
            "Событие потока",
            "canvas",
            Map.of(
                "nodes",
                List.of(Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "fm"))),
                "edges",
                List.of()));

    mockMvc
        .perform(
            post("/api/v1/rules")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("canvas_validation"))
        .andExpect(jsonPath("$.details[0].message").value("missing_stream_trigger"));
  }

  @Test
  void createRuleWithoutActionBlockReturns400() throws Exception {
    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "No action rule",
            "ruleType",
            "dedup",
            "triggerType",
            "Событие потока",
            "canvas",
            Map.of(
                "nodes",
                List.of(
                    Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                    Map.of("id", "b2", "type", "condition", "config", Map.of("field", "severity"))),
                "edges",
                List.of(Map.of("id", "e1", "source", "b1", "target", "b2"))));

    mockMvc
        .perform(
            post("/api/v1/rules")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("canvas_validation"))
        .andExpect(jsonPath("$.details[0].message").value("missing_action_block"));
  }

  @Test
  void createRuleWithOrphanNodesReturns400() throws Exception {
    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "Orphan nodes rule",
            "ruleType",
            "dedup",
            "triggerType",
            "Событие потока",
            "canvas",
            Map.of(
                "nodes",
                List.of(
                    Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                    Map.of("id", "b4", "type", "dedup"),
                    Map.of("id", "orphan", "type", "threshold")),
                "edges",
                List.of(Map.of("id", "e1", "source", "b1", "target", "b4"))));

    mockMvc
        .perform(
            post("/api/v1/rules")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("canvas_validation"))
        .andExpect(jsonPath("$.details[0].message").value("orphan_nodes"));
  }

  @Test
  void createRuleWithNotifyMissingEmailReturns400() throws Exception {
    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "Notify rule",
            "ruleType",
            "threshold",
            "triggerType",
            "Событие потока",
            "canvas",
            Map.of(
                "nodes",
                List.of(
                    Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                    Map.of("id", "b6", "type", "notify", "config", Map.of("channel", "email"))),
                "edges",
                List.of(Map.of("id", "e1", "source", "b1", "target", "b6"))));

    mockMvc
        .perform(
            post("/api/v1/rules")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("canvas_validation"))
        .andExpect(jsonPath("$.details[0].message").value("notify_invalid_email"));
  }

  @Test
  void createRuleWithPushBlockReturnsDraftRule() throws Exception {
    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "Push rule",
            "ruleType",
            "threshold",
            "triggerType",
            "Событие потока",
            "canvas",
            Map.of(
                "nodes",
                List.of(
                    Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                    Map.of("id", "b8", "type", "push", "config", Map.of("message", "Critical: {title}"))),
                "edges",
                List.of(Map.of("id", "e1", "source", "b1", "target", "b8"))));

    mockMvc
        .perform(
            post("/api/v1/rules")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Push rule"))
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void patchRuleEnableSetsEnabledTrue() throws Exception {
    String token = obtainAdminToken();
    String ruleId = findDisabledRuleId(token);

    mockMvc
        .perform(
            patch("/api/v1/rules/" + ruleId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ruleId))
        .andExpect(jsonPath("$.enabled").value(true));
  }

  @Test
  void patchRuleDisableSetsEnabledFalse() throws Exception {
    String token = obtainAdminToken();
    String ruleId = findEnabledRuleId(token);

    mockMvc
        .perform(
            patch("/api/v1/rules/" + ruleId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ruleId))
        .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void patchRuleEnableInvalidCanvasReturns400() throws Exception {
    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "Invalid enable rule",
            "ruleType",
            "dedup",
            "triggerType",
            "Событие потока",
            "description",
            "Rule with invalid canvas for enable test");

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/rules")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn();
    String ruleId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    ProcessingRuleEntity entity = processingRuleRepository.findById(java.util.UUID.fromString(ruleId)).orElseThrow();
    entity.setCanvas(
        """
        {"nodes":[
          {"id":"b1","type":"trigger","config":{"triggerType":"stream"}},
          {"id":"b2","type":"condition","config":{"field":"severity"}}
        ],"edges":[
          {"id":"e1","source":"b1","target":"b2"}
        ]}
        """);
    processingRuleRepository.save(entity);

    mockMvc
        .perform(
            patch("/api/v1/rules/" + ruleId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("canvas_validation"))
        .andExpect(jsonPath("$.details[0].message").value("missing_action_block"));
  }

  private String findDisabledRuleId(String token) throws Exception {
    MvcResult list =
        mockMvc
            .perform(get("/api/v1/rules").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode rules = objectMapper.readTree(list.getResponse().getContentAsString());
    for (JsonNode rule : rules) {
      if (!rule.get("enabled").asBoolean()) {
        return rule.get("id").asText();
      }
    }
    throw new IllegalStateException("No disabled rule found in seed data");
  }

  private String findEnabledRuleId(String token) throws Exception {
    MvcResult list =
        mockMvc
            .perform(get("/api/v1/rules").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode rules = objectMapper.readTree(list.getResponse().getContentAsString());
    for (JsonNode rule : rules) {
      if (rule.get("enabled").asBoolean()) {
        return rule.get("id").asText();
      }
    }
    throw new IllegalStateException("No enabled rule found in seed data");
  }
}
