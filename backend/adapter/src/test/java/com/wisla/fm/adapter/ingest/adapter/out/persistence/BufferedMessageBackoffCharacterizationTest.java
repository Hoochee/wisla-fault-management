package com.wisla.fm.adapter.ingest.adapter.out.persistence;

import com.wisla.fm.adapter.ingest.domain.BufferedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization test: pins the retry backoff {@code base * 2^min(retryCount - 1, 10)} as it
 * lands in the {@code buffered_messages} row. The formula used to live on the JPA entity itself;
 * it is now domain behavior on {@link BufferedEvent} that reaches the row through
 * {@link BufferedEventJpaMapper}, so the assertions are made on the mapped entity.
 */
class BufferedMessageBackoffCharacterizationTest {

    private static final int BASE_SECONDS = 30;
    private static final Instant CREATED_AT = Instant.parse("2026-07-01T12:00:00Z");

    /** Multiplier per retry attempt, starting at retryCount = 1; capped at 2^10 from the 11th on. */
    private static final long[] EXPECTED_MULTIPLIERS = {
            1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 1024, 1024
    };

    private final BufferedEventJpaMapper mapper = new BufferedEventJpaMapper();

    @Test
    void freshBufferedMessageStartsWithZeroRetries() {
        BufferedMessageJpaEntity row = mapper.toEntity(newEvent());

        assertThat(row.getRetryCount()).isZero();
    }

    @Test
    void backoffDoublesEachAttemptAndCapsAtTwoToTheTenth() {
        BufferedEvent event = newEvent();
        Instant now = CREATED_AT;

        for (int attempt = 1; attempt <= EXPECTED_MULTIPLIERS.length; attempt++) {
            long expectedDelaySeconds = BASE_SECONDS * EXPECTED_MULTIPLIERS[attempt - 1];
            now = now.plusSeconds(1);

            event.scheduleRetry(BASE_SECONDS, now);
            BufferedMessageJpaEntity row = mapper.toEntity(event);

            assertThat(row.getRetryCount())
                    .as("retry_count after attempt %d", attempt)
                    .isEqualTo(attempt);
            assertThat(row.getNextRetryAt())
                    .as("next_retry_at after attempt %d (expected +%ds)", attempt, expectedDelaySeconds)
                    .isEqualTo(now.plusSeconds(expectedDelaySeconds));
        }
    }

    @Test
    void firstRetryUsesTheBaseDelayUnmultiplied() {
        BufferedEvent event = newEvent();

        event.scheduleRetry(7, CREATED_AT);

        assertThat(mapper.toEntity(event).getNextRetryAt()).isEqualTo(CREATED_AT.plusSeconds(7));
    }

    @Test
    void capMeansTheEleventhAndEveryLaterRetryShareTheSameDelay() {
        BufferedEvent event = newEvent();
        for (int attempt = 1; attempt <= 10; attempt++) {
            event.scheduleRetry(BASE_SECONDS, CREATED_AT);
        }

        event.scheduleRetry(BASE_SECONDS, CREATED_AT);
        Instant eleventh = mapper.toEntity(event).getNextRetryAt();

        event.scheduleRetry(BASE_SECONDS, CREATED_AT);
        BufferedMessageJpaEntity twelfthRow = mapper.toEntity(event);

        long cappedDelay = BASE_SECONDS * 1024L;
        assertThat(eleventh).isEqualTo(CREATED_AT.plusSeconds(cappedDelay));
        assertThat(twelfthRow.getNextRetryAt()).isEqualTo(eleventh);
        assertThat(twelfthRow.getRetryCount()).isEqualTo(12);
    }

    @Test
    void scheduleRetryBumpsUpdatedAt() {
        BufferedEvent event = newEvent();
        Instant retriedAt = CREATED_AT.plusSeconds(120);

        event.scheduleRetry(BASE_SECONDS, retriedAt);
        BufferedMessageJpaEntity row = mapper.toEntity(event);

        assertThat(row.getUpdatedAt()).isEqualTo(retriedAt);
        assertThat(row.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    private static BufferedEvent newEvent() {
        return BufferedEvent.create(
                UUID.randomUUID(),
                "api-key",
                Map.of("event_id", "buffered-1"),
                CREATED_AT.plusSeconds(BASE_SECONDS),
                CREATED_AT
        );
    }
}
