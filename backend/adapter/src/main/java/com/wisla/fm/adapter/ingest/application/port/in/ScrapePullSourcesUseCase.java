package com.wisla.fm.adapter.ingest.application.port.in;

import java.time.Instant;

public interface ScrapePullSourcesUseCase {

    void scrapeDue(Instant now);
}
