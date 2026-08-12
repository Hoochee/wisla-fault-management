package ru.wisla.fm.ingestion.domain;

import java.util.List;

/**
 * A page of raw events enriched with the display name of their source, which the
 * {@code GET /api/v1/raw-events} contract exposes alongside every row.
 */
public record RawEventListing(
        List<Item> items,
        int page,
        int size,
        long total
) {
    public record Item(RawEvent rawEvent, String sourceName) {
    }
}
