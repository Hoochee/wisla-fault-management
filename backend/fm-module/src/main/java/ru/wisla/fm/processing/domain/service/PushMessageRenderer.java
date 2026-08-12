package ru.wisla.fm.processing.domain.service;

import ru.wisla.fm.processing.domain.Event;

/**
 * Renders the push-notification message of a push intent, lifted verbatim out of
 * {@code EventProcessingService.resolvePushMessage}.
 */
public final class PushMessageRenderer {

    public String render(String template, Event event) {
        if (template != null && !template.isBlank()) {
            return template
                    .replace("{title}", event.getTitle() != null ? event.getTitle() : "")
                    .replace("{severity}", event.getSeverity() != null ? event.getSeverity() : "");
        }
        return event.getTitle() != null ? event.getTitle() : "Событие";
    }
}
