package com.wisla.fm.adapter.ingest.application.port.out;

import com.wisla.fm.adapter.ingest.domain.SourceConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads locally cached source configuration. Expiry is not applied here — callers decide whether a
 * stale snapshot is acceptable, matching the current behavior where the buffer retry path accepts
 * one and the webhook and internal paths do not.
 */
public interface SourceConfigLookupPort {

    Optional<SourceConfig> findBySourceKey(String sourceKey);

    Optional<SourceConfig> findBySourceId(UUID sourceId);

    List<SourceConfig> findAll();
}
