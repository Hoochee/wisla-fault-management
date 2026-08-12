package ru.wisla.fm.ingestion.application.service;

import ru.wisla.fm.ingestion.application.port.in.QueryRawEventsUseCase;
import ru.wisla.fm.ingestion.application.port.out.EventSourceStatePort;
import ru.wisla.fm.ingestion.application.port.out.RawEventStorePort;
import ru.wisla.fm.ingestion.domain.RawEvent;
import ru.wisla.fm.ingestion.domain.RawEventBatch;
import ru.wisla.fm.ingestion.domain.RawEventListing;
import ru.wisla.fm.ingestion.domain.SourceIngestState;

import java.util.List;
import java.util.UUID;

public class RawEventQueryService implements QueryRawEventsUseCase {

    private static final int MAX_PAGE_SIZE = 500;

    private final RawEventStorePort rawEventStore;
    private final EventSourceStatePort eventSourceState;

    public RawEventQueryService(RawEventStorePort rawEventStore, EventSourceStatePort eventSourceState) {
        this.rawEventStore = rawEventStore;
        this.eventSourceState = eventSourceState;
    }

    @Override
    public RawEventListing query(UUID sourceId, String severity, Boolean processed, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        RawEventBatch batch = rawEventStore.findPage(sourceId, severity, processed, Math.max(page, 0), safeSize);
        List<RawEventListing.Item> items = batch.items().stream()
                .map(raw -> new RawEventListing.Item(raw, resolveSourceName(raw)))
                .toList();
        return new RawEventListing(items, batch.page(), batch.size(), batch.total());
    }

    private String resolveSourceName(RawEvent raw) {
        return eventSourceState.find(raw.sourceId())
                .map(SourceIngestState::name)
                .orElse(null);
    }
}
