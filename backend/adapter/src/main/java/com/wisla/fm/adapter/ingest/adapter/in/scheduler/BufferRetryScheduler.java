package com.wisla.fm.adapter.ingest.adapter.in.scheduler;

import com.wisla.fm.adapter.ingest.application.port.in.RetryBufferedEventsUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class BufferRetryScheduler {

    private final RetryBufferedEventsUseCase retryBufferedEvents;
    private final Clock clock;

    public BufferRetryScheduler(RetryBufferedEventsUseCase retryBufferedEvents, Clock clock) {
        this.retryBufferedEvents = retryBufferedEvents;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${wisla.adapter.buffer-retry-interval-ms:60000}")
    @Transactional
    public void retryBufferedMessages() {
        retryBufferedEvents.retryDueMessages(clock.instant());
    }
}
