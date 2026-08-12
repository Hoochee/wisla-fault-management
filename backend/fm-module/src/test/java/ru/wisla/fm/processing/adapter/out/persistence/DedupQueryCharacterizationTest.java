package ru.wisla.fm.processing.adapter.out.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.wisla.fm.processing.domain.DedupKey;
import ru.wisla.fm.processing.domain.DedupPolicy;
import ru.wisla.fm.processing.domain.Event;
import ru.wisla.fm.processing.domain.service.DedupMerger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test carried over from {@code service/DedupQueryCharacterizationTest}: it pins the
 * same dedup behavior, now driving the two classes the rule was split into — query selection in
 * {@link EventPersistenceAdapter} and merging in {@link DedupMerger} — through the same
 * {@code mergeOrCreate} sequence the use case performs.
 *
 * <p>Two properties documented here look unintended and are pinned deliberately (design decision
 * D4): {@code useCi = false} still filters on {@code ci_id IS NULL}, and {@code useSource = false}
 * / {@code useTitle = false} never actually widen the query.
 */
class DedupQueryCharacterizationTest {

    private static final List<String> ACTIVE_STATUSES = List.of("new", "in_progress", "maintenance", "deferred");
    private static final String CI_ID_VARIANT = "findFirstBySourceIdAndTitleAndCiIdAndStatusIn";
    private static final String CI_IS_NULL_VARIANT = "findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn";

    private static final UUID SOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CI_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String TITLE = "Disk usage above 90%";

    private final DedupMerger dedupMerger = new DedupMerger();

    // --- which derived query is chosen, for every key combination x ciId null / non-null --------

    @ParameterizedTest(name = "useSource={0} useTitle={1} useCi={2} ciPresent={3} -> {4}")
    @CsvSource({
            "true,  true,  true,  true,  findFirstBySourceIdAndTitleAndCiIdAndStatusIn",
            "true,  true,  true,  false, findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn",
            "true,  true,  false, true,  findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn",
            "true,  true,  false, false, findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn",
            "true,  false, true,  true,  findFirstBySourceIdAndTitleAndCiIdAndStatusIn",
            "true,  false, true,  false, findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn",
            "true,  false, false, true,  findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn",
            "true,  false, false, false, findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn",
            "false, true,  true,  true,  findFirstBySourceIdAndTitleAndCiIdAndStatusIn",
            "false, true,  true,  false, findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn",
            "false, true,  false, true,  findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn",
            "false, true,  false, false, findFirstBySourceIdAndTitleAndCiIdIsNullAndStatusIn",
            "false, false, true,  true,  findFirstBySourceIdAndTitleAndCiIdAndStatusIn",
            "false, false, true,  false, NONE",
            "false, false, false, true,  NONE",
            "false, false, false, false, NONE"
    })
    void resolvesToTheExpectedQueryVariant(
            boolean useSource, boolean useTitle, boolean useCi, boolean ciPresent, String expectedQuery) {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();

        mergeOrCreate(repository, candidate(ciPresent ? CI_ID : null, "major"),
                new DedupPolicy(useSource, useTitle, useCi));

        List<String> queries = repository.queryNames("findFirst");
        if ("NONE".equals(expectedQuery)) {
            assertThat(queries).isEmpty();
        } else {
            assertThat(queries).containsExactly(expectedQuery);
        }
        assertThat(repository.methodNames()).contains("save");
    }

    /**
     * The surprising branch of D4: disabling the CI key does not drop the CI predicate, it flips it
     * to {@code ci_id IS NULL} — so an event that has a CI can never dedup against itself.
     */
    @Test
    void useCiFalseStillFiltersOnCiIdIsNullEvenWhenTheCandidateHasACi() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();

        mergeOrCreate(repository, candidate(CI_ID, "major"), new DedupPolicy(true, true, false));

        assertThat(repository.methodNames()).doesNotContain(CI_ID_VARIANT);
        assertThat(repository.callTo(CI_IS_NULL_VARIANT).args())
                .containsExactly(SOURCE_ID, TITLE, ACTIVE_STATUSES);
    }

    /**
     * The second surprise of D4: {@code useSource} / {@code useTitle} only feed the
     * "all keys disabled" guard. Once any query runs, the candidate's own sourceId and title are
     * always passed, so those two flags never widen the match.
     */
    @Test
    void disabledSourceAndTitleKeysAreStillPassedFromTheCandidate() {
        CharacterizationEventRepository withCi = new CharacterizationEventRepository();
        mergeOrCreate(withCi, candidate(CI_ID, "major"), new DedupPolicy(false, false, true));

        assertThat(withCi.callTo(CI_ID_VARIANT).args())
                .containsExactly(SOURCE_ID, TITLE, CI_ID, ACTIVE_STATUSES);

        CharacterizationEventRepository withoutCi = new CharacterizationEventRepository();
        mergeOrCreate(withoutCi, candidate(null, "major"), new DedupPolicy(false, true, false));

        assertThat(withoutCi.callTo(CI_IS_NULL_VARIANT).args())
                .containsExactly(SOURCE_ID, TITLE, ACTIVE_STATUSES);
    }

    @Test
    void allKeysDisabledSkipsTheLookupAndSavesTheCandidate() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();
        Event candidate = candidate(CI_ID, "major");

        Event saved = mergeOrCreate(repository, candidate, new DedupPolicy(false, false, false));

        assertThat(repository.methodNames()).containsExactly("save");
        assertThat(saved.getTitle()).isEqualTo(candidate.getTitle());
        assertThat(saved.getRepeatCount()).isEqualTo(1);
    }

    /**
     * Same outcome by a different route: the CI key is enabled but the event has no CI, so all three
     * lookup keys end up null and the guard short-circuits before any query.
     */
    @Test
    void ciOnlyKeyWithoutACiAlsoSkipsTheLookup() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();

        mergeOrCreate(repository, candidate(null, "major"), new DedupPolicy(false, false, true));

        assertThat(repository.methodNames()).containsExactly("save");
    }

    @Test
    void defaultPolicyEnablesAllThreeKeys() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository();

        mergeOrCreate(repository, candidate(CI_ID, "major"), DedupPolicy.defaults());

        assertThat(DedupPolicy.defaults()).isEqualTo(new DedupPolicy(true, true, true));
        assertThat(repository.queryNames("findFirst")).containsExactly(CI_ID_VARIANT);
    }

    // --- merge behavior ------------------------------------------------------------------------

    @Test
    void mergeIncrementsRepeatCountAndStampsLastRepeatAt() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(CI_ID_VARIANT, Optional.of(entity(CI_ID, "major")));

        Instant before = Instant.now();
        Event merged = mergeOrCreate(repository, candidate(CI_ID, "major"), DedupPolicy.defaults());
        Instant after = Instant.now();

        assertThat(merged.getRepeatCount()).isEqualTo(2);
        assertThat(merged.getLastRepeatAt()).isBetween(before, after);
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
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(CI_ID_VARIANT, Optional.of(entity(CI_ID, existingSeverity)));

        Event merged = mergeOrCreate(repository, candidate(CI_ID, candidateSeverity), DedupPolicy.defaults());

        assertThat(merged.getSeverity()).isEqualTo(expected);
    }

    @Test
    void noDuplicateFoundSavesTheCandidateUntouched() {
        CharacterizationEventRepository repository = new CharacterizationEventRepository()
                .stub(CI_ID_VARIANT, Optional.empty());

        Event saved = mergeOrCreate(repository, candidate(CI_ID, "major"), DedupPolicy.defaults());

        assertThat(saved.getRepeatCount()).isEqualTo(1);
        assertThat(saved.getLastRepeatAt()).isNull();
    }

    /** The dedup step of {@code ProcessRawEventBatchService}, which replaces {@code DedupService.mergeOrCreate}. */
    private Event mergeOrCreate(CharacterizationEventRepository repository, Event candidate, DedupPolicy policy) {
        EventPersistenceAdapter adapter = new EventPersistenceAdapter(repository.asRepository(), new EventJpaMapper());
        return adapter.findActiveDuplicate(DedupKey.from(candidate, policy))
                .map(existing -> adapter.save(dedupMerger.merge(existing, candidate, Instant.now())))
                .orElseGet(() -> adapter.save(candidate));
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

    private static EventJpaEntity entity(UUID ciId, String severity) {
        return new EventJpaMapper().toJpaEntity(candidate(ciId, severity));
    }
}
