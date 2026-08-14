package com.wisla.fm.adapter.ingest.application.port.out;

import com.wisla.fm.adapter.ingest.domain.PullMetricState;

import java.util.Optional;
import java.util.UUID;

public interface PullMetricStateStorePort {

    Optional<PullMetricState> find(UUID sourceId, String externalId);

    void upsert(PullMetricState state);
}
