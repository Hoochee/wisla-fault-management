package ru.wisla.fm.notifications.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.wisla.fm.notifications.persistence.RulePushNotificationRepository;
import ru.wisla.fm.rules.persistence.ProcessingRuleRepository;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PushNotificationIntegrationTest extends AbstractFmModuleTest {

    @Autowired private ProcessingRuleRepository processingRuleRepository;
    @Autowired private RulePushNotificationRepository pushNotificationRepository;

    @Test
    void pushRuleCreatesNotificationAndApiReturnsIt() throws Exception {
        disableDedupRule();
        disableThresholdRule();
        enablePushDemoRule();

        String title = "Push test " + System.nanoTime();
        ingestEvent(title, "critical");

        assertThat(pushNotificationRepository.findAll()).isNotEmpty();

        String token = obtainAdminToken();
        String since = Instant.now().minusSeconds(60).toString();
        mockMvc
                .perform(get("/api/v1/notifications/push")
                        .param("since", since)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[?(@.title == '" + title + "')]").exists());
    }

    @AfterEach
    void restoreDefaultRules() {
        processingRuleRepository.findAll().forEach(rule -> {
            if ("dedup".equals(rule.getRuleType())) {
                rule.setEnabled(true);
            } else if ("threshold".equals(rule.getRuleType()) && rule.getName().contains("5 critical")) {
                rule.setEnabled(true);
            } else if (rule.getName().contains("Push:")) {
                rule.setEnabled(false);
            }
            processingRuleRepository.save(rule);
        });
    }

    private void disableThresholdRule() {
        processingRuleRepository.findAll().stream()
                .filter(r -> "threshold".equals(r.getRuleType()))
                .findFirst()
                .ifPresent(rule -> {
                    rule.setEnabled(false);
                    processingRuleRepository.save(rule);
                });
    }

    private void disableDedupRule() {
        processingRuleRepository.findAll().stream()
                .filter(r -> "dedup".equals(r.getRuleType()))
                .findFirst()
                .ifPresent(rule -> {
                    rule.setEnabled(false);
                    processingRuleRepository.save(rule);
                });
    }

    private void enablePushDemoRule() {
        processingRuleRepository.findAll().stream()
                .filter(r -> r.getName().contains("Push:"))
                .findFirst()
                .ifPresent(rule -> {
                    rule.setEnabled(true);
                    processingRuleRepository.save(rule);
                });
    }

    private void ingestEvent(String title, String severity) throws Exception {
        Map<String, Object> body = Map.of(
                "events",
                List.of(
                        Map.of(
                                "externalId", "ext-" + System.nanoTime(),
                                "title", title,
                                "description", "push integration test",
                                "severity", severity,
                                "occurredAt", Instant.now().toString(),
                                "nodeFqdn", "demo-server.wisla.local")));
        mockMvc
                .perform(
                        post("/api/v1/ingest")
                                .header("X-Api-Key", DEMO_SOURCE_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted());
    }
}
