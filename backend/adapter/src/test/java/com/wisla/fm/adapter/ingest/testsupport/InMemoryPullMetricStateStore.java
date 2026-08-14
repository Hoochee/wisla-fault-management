package com.wisla.fm.adapter.ingest.testsupport;

import com.wisla.fm.adapter.ingest.application.port.out.PullMetricStateStorePort;
import com.wisla.fm.adapter.ingest.domain.PullMetricState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryPullMetricStateStore implements PullMetricStateStorePort {

    private final Map<String, PullMetricState> byKey = new LinkedHashMap<>();

    @Override
    public Optional<PullMetricState> find(UUID sourceId, String externalId) {
        return Optional.ofNullable(byKey.get(key(sourceId, externalId)));
    }

    @Override
    public void upsert(PullMetricState state) {
        byKey.put(key(state.sourceId(), state.externalId()), state);
    }

    public int size() {
        return byKey.size();
    }

    private static String key(UUID sourceId, String externalId) {
        return sourceId + "|" + externalId;
    }
}
