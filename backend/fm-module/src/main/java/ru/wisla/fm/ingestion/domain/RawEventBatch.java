package ru.wisla.fm.ingestion.domain;

import java.util.List;

/**
 * One page of stored raw events, ordered {@code createdAt desc} by the store.
 */
public record RawEventBatch(
        List<RawEvent> items,
        int page,
        int size,
        long total
) {
}
