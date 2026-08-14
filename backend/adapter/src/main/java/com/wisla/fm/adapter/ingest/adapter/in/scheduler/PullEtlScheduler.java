package com.wisla.fm.adapter.ingest.adapter.in.scheduler;

import com.wisla.fm.adapter.ingest.application.port.in.ScrapePullSourcesUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class PullEtlScheduler {

    private final ScrapePullSourcesUseCase scrapePullSources;
    private final Clock clock;

    public PullEtlScheduler(ScrapePullSourcesUseCase scrapePullSources, Clock clock) {
        this.scrapePullSources = scrapePullSources;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${wisla.adapter.pull-etl-interval-ms:1000}")
    public void tick() {
        scrapePullSources.scrapeDue(clock.instant());
    }
}
