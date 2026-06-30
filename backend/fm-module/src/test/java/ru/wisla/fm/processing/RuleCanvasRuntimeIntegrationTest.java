package ru.wisla.fm.processing;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.wisla.fm.processing.persistence.EventRepository;
import ru.wisla.fm.rules.persistence.ProcessingRuleRepository;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuleCanvasRuntimeIntegrationTest extends AbstractFmModuleTest {

    @Autowired private EventRepository eventRepository;
    @Autowired private ProcessingRuleRepository processingRuleRepository;

    @Test
    void ingestDuplicateEventsIncrementsRepeatCount() throws Exception {
        String title = "Dedup test " + System.nanoTime();
        ingestEvent(title, "major");
        ingestEvent(title, "major");

        long matching = eventRepository.findAll().stream()
                .filter(e -> title.equals(e.getTitle()))
                .count();
        assertThat(matching).isEqualTo(1);

        var event = eventRepository.findAll().stream()
                .filter(e -> title.equals(e.getTitle()))
                .findFirst()
                .orElseThrow();
        assertThat(event.getRepeatCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void correlationRuleLinksChildEvent() throws Exception {
        disableDedupRule();
        enableCorrelationRule();
        String title = "Corr test " + System.nanoTime();
        ingestEvent(title, "major");
        ingestEvent(title, "major");

        var events = eventRepository.findAll().stream()
                .filter(e -> title.equals(e.getTitle()))
                .toList();
        assertThat(events).hasSize(2);

        long roots = events.stream().filter(e -> e.getRootEventId() == null).count();
        long children = events.stream().filter(e -> e.getRootEventId() != null).count();
        assertThat(roots).isEqualTo(1);
        assertThat(children).isEqualTo(1);
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

    private void enableCorrelationRule() throws Exception {
        var correlationRule = processingRuleRepository.findAll().stream()
                .filter(r -> "correlation".equals(r.getRuleType()))
                .findFirst()
                .orElseThrow();
        correlationRule.setEnabled(true);
        processingRuleRepository.save(correlationRule);
    }

    private void ingestEvent(String title, String severity) throws Exception {
        Map<String, Object> body = Map.of(
                "events",
                List.of(
                        Map.of(
                                "externalId", "ext-" + System.nanoTime(),
                                "title", title,
                                "description", "integration test",
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
