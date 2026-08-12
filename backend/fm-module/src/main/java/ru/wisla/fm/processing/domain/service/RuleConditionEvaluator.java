package ru.wisla.fm.processing.domain.service;

import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.IncomingRawEvent;
import ru.wisla.fm.processing.domain.RuleNode;

public final class RuleConditionEvaluator {

    public boolean evaluate(RuleNode node, IncomingRawEvent raw, Event event) {
        if (!"condition".equals(node.type())) {
            return true;
        }
        String field = node.config().getOrDefault("field", "severity");
        String operator = node.config().getOrDefault("operator", "eq");
        String expected = node.config().getOrDefault("value", "");
        String actual = resolveField(field, raw, event);
        return compare(actual, operator, expected);
    }

    private String resolveField(String field, IncomingRawEvent raw, Event event) {
        return switch (field) {
            case "title" -> nullToEmpty(raw != null ? raw.title() : event.getTitle());
            case "status" -> nullToEmpty(raw != null ? raw.status() : event.getStatus());
            case "nodeFqdn" -> nullToEmpty(raw != null ? raw.nodeFqdn() : event.getNodeFqdn());
            default -> nullToEmpty(raw != null ? raw.severity() : event.getSeverity());
        };
    }

    private boolean compare(String actual, String operator, String expected) {
        return switch (operator) {
            case "ne" -> !actual.equalsIgnoreCase(expected);
            case "contains" -> actual.toLowerCase().contains(expected.toLowerCase());
            default -> actual.equalsIgnoreCase(expected);
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
