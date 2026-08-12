package com.wisla.fm.adapter.ingest.adapter.in.scheduler;

import com.wisla.fm.adapter.ingest.application.port.in.SyncSourceConfigUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SourceConfigSyncScheduler implements ApplicationRunner {

    private final SyncSourceConfigUseCase syncSourceConfig;

    public SourceConfigSyncScheduler(SyncSourceConfigUseCase syncSourceConfig) {
        this.syncSourceConfig = syncSourceConfig;
    }

    @Override
    public void run(ApplicationArguments args) {
        syncSourceConfig.sync();
    }

    @Scheduled(fixedDelayString = "${wisla.adapter.config-sync-interval-ms:300000}")
    public void scheduledSync() {
        syncSourceConfig.sync();
    }
}
