package com.wisla.fm.adapter.ingest.application.port.out;

import com.wisla.fm.adapter.ingest.domain.SourceConfig;

public interface SourceConfigStorePort {

    /**
     * Inserts or replaces the snapshot of a source. {@code createdAt} of an already stored snapshot
     * is never overwritten.
     */
    void upsert(SourceConfig config);
}
