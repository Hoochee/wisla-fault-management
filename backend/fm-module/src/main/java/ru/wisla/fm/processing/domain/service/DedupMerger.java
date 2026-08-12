package ru.wisla.fm.processing.domain.service;

import ru.wisla.fm.processing.domain.Event;

import java.time.Instant;

/**
 * The merge half of the dedup rule, lifted out of {@code DedupService.mergeOrCreate}. Deciding
 * <em>which</em> active duplicate to merge into stays with {@code DedupKey} and the persistence
 * adapter (design decision D4).
 */
public final class DedupMerger {

    /** Folds the candidate into the duplicate that is already open, and returns that duplicate. */
    public Event merge(Event existing, Event candidate, Instant now) {
        existing.registerRepeat(now);
        existing.escalateSeverity(candidate.getSeverity());
        return existing;
    }
}
