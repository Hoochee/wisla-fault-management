package com.wisla.fm.adapter.ingest.application.service;

import com.wisla.fm.adapter.ingest.application.port.in.SyncSourceConfigUseCase;
import com.wisla.fm.adapter.ingest.application.port.out.FmModuleSourceConfigPort;
import com.wisla.fm.adapter.ingest.application.port.out.FmModuleSourceConfigPort.RemoteSourceConfig;
import com.wisla.fm.adapter.ingest.application.port.out.SourceConfigStorePort;
import com.wisla.fm.adapter.ingest.domain.FilterRules;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class SyncSourceConfigService implements SyncSourceConfigUseCase {

    private static final String ACTIVE_STATUS = "active";
    private static final long TTL_SECONDS = 86_400L;

    private final FmModuleSourceConfigPort fmModuleSourceConfig;
    private final SourceConfigStorePort sourceConfigStore;
    private final Clock clock;
    private final String fmModuleBaseUrl;

    public SyncSourceConfigService(
            FmModuleSourceConfigPort fmModuleSourceConfig,
            SourceConfigStorePort sourceConfigStore,
            Clock clock,
            String fmModuleBaseUrl
    ) {
        this.fmModuleSourceConfig = fmModuleSourceConfig;
        this.sourceConfigStore = sourceConfigStore;
        this.clock = clock;
        this.fmModuleBaseUrl = fmModuleBaseUrl;
    }

    @Override
    public void sync() {
        try {
            List<RemoteSourceConfig> sources = fmModuleSourceConfig.fetchSources();
            if (sources == null) {
                return;
            }
            Instant now = clock.instant();
            Instant ttl = now.plusSeconds(TTL_SECONDS);
            for (RemoteSourceConfig source : sources) {
                sourceConfigStore.upsert(toSourceConfig(source, now, ttl));
            }
        } catch (Exception ignored) {
            // fm-module may not be ready on first boot; retry on schedule
        }
    }

    private SourceConfig toSourceConfig(RemoteSourceConfig source, Instant now, Instant ttl) {
        return new SourceConfig(
                source.sourceId(),
                source.sourceKey(),
                source.apiKeyHash(),
                fmModuleBaseUrl,
                FilterRules.of(source.filterRules()),
                !ACTIVE_STATUS.equals(source.status()),
                ttl,
                now,
                now
        );
    }
}
