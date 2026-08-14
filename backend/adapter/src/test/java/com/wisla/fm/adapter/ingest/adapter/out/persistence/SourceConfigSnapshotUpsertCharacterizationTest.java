package com.wisla.fm.adapter.ingest.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test: pins the upsert semantics of
 * {@link SourceConfigSnapshotJpaEntity#replace} that {@code SourceConfigStorePort} now exposes. The
 * load-bearing detail is that {@code created_at} survives a replace on an already-created snapshot.
 */
class SourceConfigSnapshotUpsertCharacterizationTest {

    private static final Instant FIRST_SYNC = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant SECOND_SYNC = Instant.parse("2026-01-02T11:30:00Z");

    @Test
    void firstReplaceSetsCreatedAtAndUpdatedAtToTheSameInstant() {
        SourceConfigSnapshotJpaEntity snapshot = SourceConfigSnapshotJpaEntity.createEmpty();

        replace(snapshot, "source-key", false, FIRST_SYNC);

        assertThat(snapshot.getCreatedAt()).isEqualTo(FIRST_SYNC);
        assertThat(snapshot.getUpdatedAt()).isEqualTo(FIRST_SYNC);
    }

    @Test
    void replaceOnAnExistingSnapshotDoesNotOverwriteCreatedAt() {
        SourceConfigSnapshotJpaEntity snapshot = SourceConfigSnapshotJpaEntity.createEmpty();
        replace(snapshot, "source-key", false, FIRST_SYNC);

        replace(snapshot, "source-key", false, SECOND_SYNC);

        assertThat(snapshot.getCreatedAt()).isEqualTo(FIRST_SYNC);
        assertThat(snapshot.getUpdatedAt()).isEqualTo(SECOND_SYNC);
    }

    @Test
    void replaceOverwritesEveryOtherField() {
        UUID firstSourceId = UUID.randomUUID();
        UUID secondSourceId = UUID.randomUUID();
        Instant firstTtl = FIRST_SYNC.plusSeconds(86_400);
        Instant secondTtl = SECOND_SYNC.plusSeconds(86_400);

        SourceConfigSnapshotJpaEntity snapshot = SourceConfigSnapshotJpaEntity.createEmpty();
        snapshot.replace(firstSourceId, "old-key", "old-hash", "http://old", Map.of("enabled", false),
                false, firstTtl, FIRST_SYNC);

        snapshot.replace(secondSourceId, "new-key", "new-hash", "http://new", Map.of("enabled", true),
                true, secondTtl, SECOND_SYNC);

        assertThat(snapshot.getSourceId()).isEqualTo(secondSourceId);
        assertThat(snapshot.getSourceKey()).isEqualTo("new-key");
        assertThat(snapshot.getApiKeyHash()).isEqualTo("new-hash");
        assertThat(snapshot.getEndpoint()).isEqualTo("http://new");
        assertThat(snapshot.getFilterRules()).containsEntry("enabled", true);
        assertThat(snapshot.isBlocked()).isTrue();
        assertThat(snapshot.getTtlExpiresAt()).isEqualTo(secondTtl);
    }

    @Test
    void replaceOverwritesPullEtlFields() {
        SourceConfigSnapshotJpaEntity snapshot = SourceConfigSnapshotJpaEntity.createEmpty();
        snapshot.replace(
                UUID.randomUUID(),
                "giftshop-metrics",
                "hash",
                "http://fm-module:8080",
                Map.of(),
                false,
                FIRST_SYNC.plusSeconds(86_400),
                FIRST_SYNC,
                "pull_etl",
                "30s",
                Map.of("rules", Map.of("metric", "up"))
        );

        assertThat(snapshot.getSourceType()).isEqualTo("pull_etl");
        assertThat(snapshot.getSchedule()).isEqualTo("30s");
        assertThat(snapshot.getParserConfig()).containsKey("rules");
    }

    @Test
    void nullFilterRulesBecomeAnEmptyMap() {
        SourceConfigSnapshotJpaEntity snapshot = SourceConfigSnapshotJpaEntity.createEmpty();

        snapshot.replace(UUID.randomUUID(), "source-key", "hash", "http://fm-module:8080", null,
                false, FIRST_SYNC.plusSeconds(86_400), FIRST_SYNC);

        assertThat(snapshot.getFilterRules()).isEmpty();
    }

    @Test
    void expiryIsDecidedByTtlExpiresAtAgainstWallClockTime() {
        SourceConfigSnapshotJpaEntity expired = SourceConfigSnapshotJpaEntity.createEmpty();
        expired.replace(UUID.randomUUID(), "expired", "hash", "http://fm-module:8080", Map.of(),
                false, Instant.now().minusSeconds(1), Instant.now());

        SourceConfigSnapshotJpaEntity fresh = SourceConfigSnapshotJpaEntity.createEmpty();
        fresh.replace(UUID.randomUUID(), "fresh", "hash", "http://fm-module:8080", Map.of(),
                false, Instant.now().plusSeconds(3_600), Instant.now());

        assertThat(expired.isExpired()).isTrue();
        assertThat(fresh.isExpired()).isFalse();
    }

    private static void replace(
            SourceConfigSnapshotJpaEntity snapshot,
            String sourceKey,
            boolean blocked,
            Instant now
    ) {
        snapshot.replace(
                UUID.randomUUID(),
                sourceKey,
                "api-key-hash",
                "http://fm-module:8080",
                Map.of("enabled", false),
                blocked,
                now.plusSeconds(86_400),
                now
        );
    }
}
