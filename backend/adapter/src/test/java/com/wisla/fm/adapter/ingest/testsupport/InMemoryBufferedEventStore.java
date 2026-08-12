package com.wisla.fm.adapter.ingest.testsupport;

import com.wisla.fm.adapter.ingest.application.port.out.BufferedEventStorePort;
import com.wisla.fm.adapter.ingest.domain.BufferedEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InMemoryBufferedEventStore implements BufferedEventStorePort {

    private final Map<UUID, BufferedEvent> stored = new LinkedHashMap<>();
    private final List<UUID> deleted = new ArrayList<>();
    private int saveCount;

    public void put(BufferedEvent event) {
        stored.put(event.id(), event);
    }

    public List<BufferedEvent> all() {
        return List.copyOf(stored.values());
    }

    public List<UUID> deletedIds() {
        return List.copyOf(deleted);
    }

    public int saveCount() {
        return saveCount;
    }

    @Override
    public BufferedEvent save(BufferedEvent event) {
        saveCount++;
        stored.put(event.id(), event);
        return event;
    }

    @Override
    public List<BufferedEvent> findDue(Instant now) {
        return stored.values().stream()
                .filter(event -> !event.nextRetryAt().isAfter(now))
                .sorted(Comparator.comparing(BufferedEvent::nextRetryAt))
                .toList();
    }

    @Override
    public void delete(BufferedEvent event) {
        stored.remove(event.id());
        deleted.add(event.id());
    }

    @Override
    public long count() {
        return stored.size();
    }
}
