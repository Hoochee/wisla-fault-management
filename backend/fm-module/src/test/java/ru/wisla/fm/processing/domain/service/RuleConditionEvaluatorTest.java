package ru.wisla.fm.processing.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.IncomingRawEvent;
import ru.wisla.fm.processing.domain.RuleNode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuleConditionEvaluatorTest {

    private RuleConditionEvaluator evaluator;
    private IncomingRawEvent raw;
    private Event event;

    @BeforeEach
    void setUp() {
        evaluator = new RuleConditionEvaluator();
        raw = raw("critical");

        event = new Event();
        event.setSeverity("critical");
        event.setTitle("Disk full");
        event.setStatus("new");
        event.setNodeFqdn("host.example.com");
    }

    @Test
    void eqOperatorMatchesSeverity() {
        RuleNode node = RuleNode.fromMap(Map.of(
                "id", "c1",
                "type", "condition",
                "config", Map.of("field", "severity", "operator", "eq", "value", "critical")
        ));
        assertThat(evaluator.evaluate(node, raw, event)).isTrue();
    }

    @Test
    void eqOperatorRejectsNonMatchingSeverity() {
        raw = raw("warning");
        RuleNode node = RuleNode.fromMap(Map.of(
                "id", "c1",
                "type", "condition",
                "config", Map.of("field", "severity", "operator", "eq", "value", "critical")
        ));
        assertThat(evaluator.evaluate(node, raw, event)).isFalse();
    }

    @Test
    void containsOperatorMatchesTitle() {
        RuleNode node = RuleNode.fromMap(Map.of(
                "id", "c1",
                "type", "condition",
                "config", Map.of("field", "title", "operator", "contains", "value", "disk")
        ));
        assertThat(evaluator.evaluate(node, raw, event)).isTrue();
    }

    @Test
    void neOperatorMatchesNodeFqdn() {
        RuleNode node = RuleNode.fromMap(Map.of(
                "id", "c1",
                "type", "condition",
                "config", Map.of("field", "nodeFqdn", "operator", "ne", "value", "other.example.com")
        ));
        assertThat(evaluator.evaluate(node, raw, event)).isTrue();
    }

    private static IncomingRawEvent raw(String severity) {
        return new IncomingRawEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ext-1",
                "Disk full",
                null,
                severity,
                "new",
                "host.example.com",
                null,
                "{}",
                Instant.parse("2026-01-01T10:00:00Z"),
                false);
    }
}
