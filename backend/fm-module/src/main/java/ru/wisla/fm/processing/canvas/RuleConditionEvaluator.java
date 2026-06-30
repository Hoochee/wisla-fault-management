package ru.wisla.fm.processing.canvas;

import org.springframework.stereotype.Component;
import ru.wisla.fm.ingestion.domain.RawEventEntity;
import ru.wisla.fm.processing.domain.EventEntity;

@Component
public class RuleConditionEvaluator {

    public boolean evaluate(CanvasNodeView node, RawEventEntity raw, EventEntity event) {
        if (!"condition".equals(node.type())) {
            return true;
        }
        String field = node.config().getOrDefault("field", "severity");
        String operator = node.config().getOrDefault("operator", "eq");
        String expected = node.config().getOrDefault("value", "");
        String actual = resolveField(field, raw, event);
        return compare(actual, operator, expected);
    }

    private String resolveField(String field, RawEventEntity raw, EventEntity event) {
        return switch (field) {
            case "title" -> nullToEmpty(raw != null ? raw.getTitle() : event.getTitle());
            case "status" -> nullToEmpty(raw != null ? raw.getStatus() : event.getStatus());
            case "nodeFqdn" -> nullToEmpty(raw != null ? raw.getNodeFqdn() : event.getNodeFqdn());
            default -> nullToEmpty(raw != null ? raw.getSeverity() : event.getSeverity());
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
