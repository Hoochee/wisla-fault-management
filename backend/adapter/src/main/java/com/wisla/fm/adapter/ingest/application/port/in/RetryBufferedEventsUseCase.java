package com.wisla.fm.adapter.ingest.application.port.in;

import java.time.Instant;

public interface RetryBufferedEventsUseCase {

    void retryDueMessages(Instant now);
}
