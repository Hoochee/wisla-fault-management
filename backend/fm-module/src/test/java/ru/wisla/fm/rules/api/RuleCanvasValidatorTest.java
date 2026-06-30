package ru.wisla.fm.rules.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleCanvasValidatorTest {

    private final RuleCanvasValidator validator = new RuleCanvasValidator();

    @Test
    void notifyWithEmailChannelRequiresValidEmailAddress() {
        RuleCanvasDto canvas = canvasWithNotify(Map.of("channel", "email"));

        assertThatThrownBy(() -> validator.validate(canvas))
                .isInstanceOf(CanvasValidationException.class)
                .satisfies(ex -> {
                    CanvasValidationException cve = (CanvasValidationException) ex;
                    assertDetail(cve, "notify_invalid_email");
                });
    }

    @Test
    void notifyWithEmailChannelAcceptsValidEmailAddress() {
        RuleCanvasDto canvas = canvasWithNotify(
                Map.of("channel", "email", "emailAddress", "ops@wisla.local"));
        validator.validate(canvas);
    }

    @Test
    void notifyWithTelegramChannelDoesNotRequireEmail() {
        RuleCanvasDto canvas = canvasWithNotify(Map.of("channel", "telegram"));
        validator.validate(canvas);
    }

    @Test
    void pushBlockIsValidAction() {
        RuleCanvasDto canvas = new RuleCanvasDto(
                List.of(
                        Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                        Map.of("id", "b8", "type", "push", "config", Map.of("message", "Critical: {title}"))),
                List.of(Map.of("id", "e1", "source", "b1", "target", "b8")));
        validator.validate(canvas);
    }

    private RuleCanvasDto canvasWithNotify(Map<String, String> notifyConfig) {
        return new RuleCanvasDto(
                List.of(
                        Map.of("id", "b1", "type", "trigger", "config", Map.of("triggerType", "stream")),
                        Map.of("id", "b6", "type", "notify", "config", notifyConfig)),
                List.of(Map.of("id", "e1", "source", "b1", "target", "b6")));
    }

    private static void assertDetail(CanvasValidationException ex, String message) {
        org.assertj.core.api.Assertions.assertThat(ex.getDetails())
                .anyMatch(d -> message.equals(d.message()));
    }
}
