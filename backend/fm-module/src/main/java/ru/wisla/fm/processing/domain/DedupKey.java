package ru.wisla.fm.processing.domain;

import java.util.UUID;

/**
 * The lookup key for an active duplicate, derived from a candidate event and a {@link DedupPolicy}.
 *
 * <p>Two properties of {@code DedupService.findActiveDuplicate} are carried through deliberately
 * (design decision D4, pinned by {@code DedupQueryCharacterizationTest}):
 *
 * <ul>
 *   <li>{@code useCi = false} does not drop the CI predicate, it flips it to {@code ci_id IS NULL} —
 *       so {@link #useCi()} is kept only to reproduce the redundant-looking
 *       {@code ciId == null && useCi} branch in the persistence adapter.</li>
 *   <li>{@code useSource = false} / {@code useTitle = false} never widen the match: they only feed
 *       {@link #lookupRequired()}, because the query always receives the candidate's own
 *       {@code sourceId} and {@code title}.</li>
 * </ul>
 */
public record DedupKey(UUID sourceId, String title, UUID ciId, boolean useCi, boolean lookupRequired) {

    public static DedupKey from(Event candidate, DedupPolicy policy) {
        UUID sourceId = policy.useSource() ? candidate.getSourceId() : null;
        String title = policy.useTitle() ? candidate.getTitle() : null;
        UUID ciId = policy.useCi() ? candidate.getCiId() : null;
        boolean lookupRequired = sourceId != null || title != null || ciId != null;
        return new DedupKey(candidate.getSourceId(), candidate.getTitle(), ciId, policy.useCi(), lookupRequired);
    }
}
