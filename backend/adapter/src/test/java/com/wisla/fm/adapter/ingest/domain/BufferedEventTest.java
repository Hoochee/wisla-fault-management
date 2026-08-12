package com.wisla.fm.adapter.ingest.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors the {@code BufferedMessage.scheduleRetry(int)} characterization test against the domain
 * API {@link BufferedEvent#scheduleRetry(int, Instant)}: {@code base * 2^min(retryCount - 1, 10)}.
 */
class BufferedEventTest {

    private static final int BASE_SECONDS = 30;
    private static final Instant CREATED_AT = Instant.parse("2026-07-01T12:00:00Z");

    /** Multiplier per retry attempt, starting at retryCount = 1; capped at 2^10 from the 11th on. */
    private static final long[] EXPECTED_MULTIPLIERS = {
            1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 1024, 1024
    };

    @Test
    void freshBufferedEventStartsWithZeroRetries() {
        BufferedEvent event = newEvent();

        assertThat(event.retryCount()).isZero();
    }

    @Test
    void createAssignsAnIdAndAlignsCreatedAtWithUpdatedAt() {
        BufferedEvent event = newEvent();

        assertThat(event.id()).isNotNull();
        assertThat(event.createdAt()).isEqualTo(CREATED_AT);
        assertThat(event.updatedAt()).isEqualTo(CREATED_AT);
        assertThat(event.nextRetryAt()).isEqualTo(CREATED_AT.plusSeconds(BASE_SECONDS));
    }

    @Test
    void backoffDoublesEachAttemptAndCapsAtTwoToTheTenth() {
        BufferedEvent event = newEvent();
        Instant now = CREATED_AT;

        for (int attempt = 1; attempt <= EXPECTED_MULTIPLIERS.length; attempt++) {
            long expectedDelaySeconds = BASE_SECONDS * EXPECTED_MULTIPLIERS[attempt - 1];
            now = now.plusSeconds(1);

            event.scheduleRetry(BASE_SECONDS, now);

            assertThat(event.retryCount())
                    .as("retryCount after attempt %d", attempt)
                    .isEqualTo(attempt);
            assertThat(event.nextRetryAt())
                    .as("nextRetryAt after attempt %d (expected +%ds)", attempt, expectedDelaySeconds)
                    .isEqualTo(now.plusSeconds(expectedDelaySeconds));
        }
    }

    @Test
    void firstRetryUsesTheBaseDelayUnmultiplied() {
        BufferedEvent event = newEvent();

        event.scheduleRetry(7, CREATED_AT);

        assertThat(event.nextRetryAt()).isEqualTo(CREATED_AT.plusSeconds(7));
    }

    @Test
    void capMeansTheEleventhAndEveryLaterRetryShareTheSameDelay() {
        BufferedEvent event = newEvent();
        for (int attempt = 1; attempt <= 10; attempt++) {
            event.scheduleRetry(BASE_SECONDS, CREATED_AT);
        }

        event.scheduleRetry(BASE_SECONDS, CREATED_AT);
        Instant eleventh = event.nextRetryAt();

        event.scheduleRetry(BASE_SECONDS, CREATED_AT);
        Instant twelfth = event.nextRetryAt();

        long cappedDelay = BASE_SECONDS * 1024L;
        assertThat(eleventh).isEqualTo(CREATED_AT.plusSeconds(cappedDelay));
        assertThat(twelfth).isEqualTo(eleventh);
        assertThat(event.retryCount()).isEqualTo(12);
    }

    @Test
    void scheduleRetryBumpsUpdatedAtFromTheSuppliedInstantAndLeavesCreatedAt() {
        BufferedEvent event = newEvent();
        Instant retriedAt = CREATED_AT.plusSeconds(120);

        event.scheduleRetry(BASE_SECONDS, retriedAt);

        assertThat(event.updatedAt()).isEqualTo(retriedAt);
        assertThat(event.createdAt()).isEqualTo(CREATED_AT);
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
