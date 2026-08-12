package com.wisla.fm.adapter.ingest.application.port.out;

import com.wisla.fm.adapter.ingest.domain.BufferedEvent;

import java.time.Instant;
import java.util.List;

public interface BufferedEventStorePort {

    BufferedEvent save(BufferedEvent event);

    List<BufferedEvent> findDue(Instant now);

    void delete(BufferedEvent event);

    long count();
}
