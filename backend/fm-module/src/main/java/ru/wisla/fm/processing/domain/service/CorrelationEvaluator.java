package ru.wisla.fm.processing.domain.service;

import ru.wisla.fm.processing.domain.CorrelationPolicy;
import ru.wisla.fm.processing.domain.Event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The correlation rule, lifted out of {@code CorrelationService.evaluateAfterProcessing}: the oldest
 * event in the window becomes the root, an already-rooted window root is adopted transitively, and
 * an event never becomes its own root.
 *
 * <p>Choosing between the six derived window queries stays a persistence concern (design decision
 * D4), so the configured {@code matchField} is handed to {@link Window} untouched — including an
 * unrecognised one, which the query layer resolves to the title window.
 */
public final class CorrelationEvaluator {

    /** The window side of the rule; implemented over {@code EventStorePort}. */
    public interface Window {

        List<Event> findWindow(Event processedEvent, String matchField, Instant since);

        Optional<Event> findById(UUID id);
    }

    /** Returns {@code true} when the processed event was given a root and has to be saved. */
    public boolean evaluate(Event processedEvent, CorrelationPolicy policy, Instant now, Window window) {
        Instant since = now.minus(policy.windowMin(), ChronoUnit.MINUTES);
        List<Event> windowEvents = window.findWindow(processedEvent, policy.matchField(), since);
        if (windowEvents.size() < policy.count()) {
            return false;
        }
        Event root = windowEvents.getFirst();
        if (root.getRootEventId() != null) {
            root = window.findById(root.getRootEventId()).orElse(root);
        }
        if (processedEvent.getId().equals(root.getId())) {
            return false;
        }
        processedEvent.assignRoot(root.getId());
        return true;
    }
}
