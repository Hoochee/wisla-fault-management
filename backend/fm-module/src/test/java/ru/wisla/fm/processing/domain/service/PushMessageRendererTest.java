package ru.wisla.fm.processing.domain.service;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.processing.domain.Event;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the push-message template resolution lifted out of
 * {@code EventProcessingService.resolvePushMessage}.
 */
class PushMessageRendererTest {

    private final PushMessageRenderer renderer = new PushMessageRenderer();

    @Test
    void substitutesTitleAndSeverity() {
        assertThat(renderer.render("{severity}: {title}", event("Disk full", "critical")))
                .isEqualTo("critical: Disk full");
    }

    @Test
    void substitutesEveryOccurrenceOfAPlaceholder() {
        assertThat(renderer.render("{title} / {title}", event("Disk full", "critical")))
                .isEqualTo("Disk full / Disk full");
    }

    @Test
    void aNullTitleOrSeverityBecomesAnEmptyPlaceholder() {
        assertThat(renderer.render("[{title}][{severity}]", event(null, null))).isEqualTo("[][]");
    }

    @Test
    void anUnknownPlaceholderIsLeftAlone() {
        assertThat(renderer.render("{nodeFqdn} down", event("Disk full", "critical")))
                .isEqualTo("{nodeFqdn} down");
    }

    @Test
    void aNullTemplateFallsBackToTheEventTitle() {
        assertThat(renderer.render(null, event("Disk full", "critical"))).isEqualTo("Disk full");
    }

    @Test
    void aBlankTemplateFallsBackToTheEventTitle() {
        assertThat(renderer.render("   ", event("Disk full", "critical"))).isEqualTo("Disk full");
    }

    @Test
    void withoutATemplateOrATitleTheFinalDefaultIsUsed() {
        assertThat(renderer.render(null, event(null, "critical"))).isEqualTo("Событие");
        assertThat(renderer.render("", event(null, "critical"))).isEqualTo("Событие");
    }

    private static Event event(String title, String severity) {
        Event event = new Event();
        event.setTitle(title);
        event.setSeverity(severity);
        return event;
    }
}
