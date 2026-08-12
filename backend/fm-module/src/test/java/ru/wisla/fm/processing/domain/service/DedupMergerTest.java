package ru.wisla.fm.processing.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.wisla.fm.processing.domain.DedupKey;
import ru.wisla.fm.processing.domain.DedupPolicy;
import ru.wisla.fm.processing.domain.Event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors the merge and key-derivation assertions pinned against the current {@code DedupService}
 * by {@code DedupQueryCharacterizationTest} (task 1.4), now against the domain types: the merge rule
 * lives in {@link DedupMerger}, the key derivation in {@link DedupKey}.
 */
class DedupMergerTest {

    private static final UUID SOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CI_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String TITLE = "Disk usage above 90%";

    private final DedupMerger merger = new DedupMerger();

    // --- merge behavior ------------------------------------------------------------------------

    @Test
    void mergeIncrementsRepeatCountAndStampsLastRepeatAt() {
        Event existing = candidate(CI_ID, "major");
        Instant now = Instant.parse("2026-02-03T08:15:00Z");

        Event merged = merger.merge(existing, candidate(CI_ID, "major"), now);

        assertThat(merged).isSameAs(existing);
        assertThat(merged.getRepeatCount()).isEqualTo(2);
        assertThat(merged.getLastRepeatAt()).isEqualTo(now);
    }

    @ParameterizedTest(name = "existing={0} candidate={1} -> {2}")
    @CsvSource({
            "major,        critical,      critical",
            "critical,     major,         critical",
            "major,        major,         major",
            "critical,     fatal,         fatal",
            "fatal,        critical,      fatal",
            "minor,        warning,       minor",
            "warning,      minor,         minor",
            "warning,      informational, warning",
            "informational,warning,       warning"
    })
    void mergeEscalatesSeverityOnlyUpwards(String existingSeverity, String candidateSeverity, String expected) {
        Event existing = candidate(CI_ID, existingSeverity);

        Event merged = merger.merge(existing, candidate(CI_ID, candidateSeverity), Instant.now());

        assertThat(merged.getSeverity()).isEqualTo(expected);
    }

    @Test
    void mergeDoesNotTouchTheCandidate() {
        Event existing = candidate(CI_ID, "critical");
        Event incoming = candidate(CI_ID, "major");

        merger.merge(existing, incoming, Instant.now());

        assertThat(incoming.getRepeatCount()).isEqualTo(1);
        assertThat(incoming.getLastRepeatAt()).isNull();
        assertThat(incoming.getSeverity()).isEqualTo("major");
    }

    // --- key derivation, including the two D4 surprises -----------------------------------------

    @Test
    void allKeysDisabledRequiresNoLookupSoTheCandidateBecomesANewEvent() {
        DedupKey key = DedupKey.from(candidate(CI_ID, "major"), new DedupPolicy(false, false, false));

        assertThat(key.lookupRequired()).isFalse();
    }

    /** Same outcome by a different route: the CI key is on but the event has no CI. */
    @Test
    void ciOnlyKeyWithoutACiAlsoRequiresNoLookup() {
        DedupKey key = DedupKey.from(candidate(null, "major"), new DedupPolicy(false, false, true));

        assertThat(key.lookupRequired()).isFalse();
    }

    @ParameterizedTest(name = "useSource={0} useTitle={1} useCi={2} ciPresent={3} -> lookup={4}")
    @CsvSource({
            "true,  true,  true,  true,  true",
            "true,  true,  true,  false, true",
            "true,  true,  false, true,  true",
            "true,  true,  false, false, true",
            "true,  false, true,  true,  true",
            "true,  false, true,  false, true",
            "true,  false, false, true,  true",
            "true,  false, false, false, true",
            "false, true,  true,  true,  true",
            "false, true,  true,  false, true",
            "false, true,  false, true,  true",
            "false, true,  false, false, true",
            "false, false, true,  true,  true",
            "false, false, true,  false, false",
            "false, false, false, true,  false",
            "false, false, false, false, false"
    })
    void lookupIsRequiredForEveryCombinationExceptTheThreeAllNullOnes(
            boolean useSource, boolean useTitle, boolean useCi, boolean ciPresent, boolean lookupRequired) {
        DedupKey key = DedupKey.from(
                candidate(ciPresent ? CI_ID : null, "major"), new DedupPolicy(useSource, useTitle, useCi));

        assertThat(key.lookupRequired()).isEqualTo(lookupRequired);
    }

    /**
     * The surprising branch of D4: disabling the CI key does not drop the CI predicate. The key
     * carries {@code ciId = null} together with {@code useCi = false}, and the persistence adapter
     * turns both into the {@code ...CiIdIsNull...} query variant.
     */
    @Test
    void useCiFalseClearsTheCiIdEvenWhenTheCandidateHasACi() {
        DedupKey key = DedupKey.from(candidate(CI_ID, "major"), new DedupPolicy(true, true, false));

        assertThat(key.ciId()).isNull();
        assertThat(key.useCi()).isFalse();
    }

    @Test
    void useCiTrueWithoutACiKeepsTheRedundantLookingBranchDistinguishable() {
        DedupKey key = DedupKey.from(candidate(null, "major"), new DedupPolicy(true, true, true));

        assertThat(key.ciId()).isNull();
        assertThat(key.useCi()).isTrue();
    }

    /**
     * The second surprise of D4: {@code useSource} / {@code useTitle} only feed the lookup guard.
     * Once a lookup happens, the candidate's own sourceId and title are always the query arguments,
     * so those two flags never widen the match.
     */
    @Test
    void disabledSourceAndTitleKeysAreStillTakenFromTheCandidate() {
        DedupKey withCi = DedupKey.from(candidate(CI_ID, "major"), new DedupPolicy(false, false, true));

        assertThat(withCi.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(withCi.title()).isEqualTo(TITLE);
        assertThat(withCi.ciId()).isEqualTo(CI_ID);

        DedupKey withoutCi = DedupKey.from(candidate(null, "major"), new DedupPolicy(false, true, false));

        assertThat(withoutCi.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(withoutCi.title()).isEqualTo(TITLE);
        assertThat(withoutCi.ciId()).isNull();
    }

    @Test
    void defaultPolicyEnablesAllThreeKeys() {
        assertThat(DedupPolicy.defaults()).isEqualTo(new DedupPolicy(true, true, true));
    }

    @Test
    void activeStatusSetIsUnchanged() {
        assertThat(Event.ACTIVE_STATUSES)
                .isEqualTo(List.of("new", "in_progress", "maintenance", "deferred"));
    }

    private static Event candidate(UUID ciId, String severity) {
        Event event = new Event();
        event.setStatus("new");
        event.setSeverity(severity);
        event.setTitle(TITLE);
        event.setSourceId(SOURCE_ID);
        event.setCiId(ciId);
        event.setSourceAt(Instant.parse("2026-01-01T10:00:00Z"));
        return event;
    }
}
