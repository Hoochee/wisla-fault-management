package com.wisla.fm.adapter.ingest.application.service;

import com.wisla.fm.adapter.ingest.application.port.out.FmModuleSourceConfigPort.RemoteSourceConfig;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;
import com.wisla.fm.adapter.ingest.testsupport.InMemorySourceConfigStore;
import com.wisla.fm.adapter.ingest.testsupport.StubFmModuleSourceConfigPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SyncSourceConfigServiceTest {

    private static final UUID SOURCE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final String FM_MODULE_BASE_URL = "http://fm-module:8080";
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final long TTL_SECONDS = 86_400L;

    private final InMemorySourceConfigStore sourceConfigs = new InMemorySourceConfigStore();
    private final StubFmModuleSourceConfigPort fmModule = new StubFmModuleSourceConfigPort();

    private final SyncSourceConfigService service = new SyncSourceConfigService(
            fmModule,
            sourceConfigs,
            CLOCK,
            FM_MODULE_BASE_URL
    );

    @Test
    void activeRemoteSourceIsUpsertedWithTheAdapterEndpointAndADayLongTtl() {
        fmModule.returning(List.of(remote("active", Map.of("enabled", true))));

        service.sync();

        assertThat(sourceConfigs.all()).hasSize(1);
        SourceConfig stored = sourceConfigs.all().getFirst();
        assertThat(stored.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(stored.sourceKey()).isEqualTo("zabbix-prod-01");
        assertThat(stored.apiKeyHash()).isEqualTo("stored-hash");
        assertThat(stored.endpoint()).isEqualTo(FM_MODULE_BASE_URL);
        assertThat(stored.filterRules().asMap()).containsEntry("enabled", true);
        assertThat(stored.blocked()).isFalse();
        assertThat(stored.ttlExpiresAt()).isEqualTo(NOW.plusSeconds(TTL_SECONDS));
        assertThat(stored.createdAt()).isEqualTo(NOW);
        assertThat(stored.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void anyStatusOtherThanActiveMarksTheSourceBlocked() {
        fmModule.returning(List.of(remote("paused", Map.of())));

        service.sync();

        assertThat(sourceConfigs.all().getFirst().blocked()).isTrue();
    }

    @Test
    void nullFilterRulesBecomeAnEmptyRuleSet() {
        fmModule.returning(List.of(remote("active", null)));

        service.sync();

        assertThat(sourceConfigs.all().getFirst().filterRules().asMap()).isEmpty();
    }

    @Test
    void everyReturnedSourceIsUpserted() {
        fmModule.returning(List.of(
                remote(UUID.randomUUID(), "src-a", "active", Map.of()),
                remote(UUID.randomUUID(), "src-b", "inactive", Map.of())
        ));

        service.sync();

        assertThat(sourceConfigs.all()).hasSize(2);
    }

    @Test
    void anEmptyResponseChangesNothing() {
        fmModule.returning(List.of());

        service.sync();

        assertThat(sourceConfigs.all()).isEmpty();
    }

    @Test
    void aPortFailureIsSwallowedSoTheSchedulerKeepsRunning() {
        fmModule.failingWith(new IllegalStateException("fm-module not ready"));

        assertThatCode(service::sync).doesNotThrowAnyException();
        assertThat(fmModule.callCount()).isEqualTo(1);
        assertThat(sourceConfigs.all()).isEmpty();
    }

    @Test
    void resyncPreservesTheOriginalCreatedAt() {
        fmModule.returning(List.of(remote("active", Map.of())));
        service.sync();

        SyncSourceConfigService later = new SyncSourceConfigService(
                fmModule,
                sourceConfigs,
                Clock.fixed(NOW.plusSeconds(TTL_SECONDS), ZoneOffset.UTC),
                FM_MODULE_BASE_URL
        );
        later.sync();

        SourceConfig stored = sourceConfigs.all().getFirst();
        assertThat(stored.createdAt()).isEqualTo(NOW);
        assertThat(stored.updatedAt()).isEqualTo(NOW.plusSeconds(TTL_SECONDS));
    }

    @Test
    void pullEtlFieldsAreCopiedOntoTheSnapshot() {
        Map<String, Object> parserConfig = Map.of(
                "targets", List.of(Map.of("url", "http://giftshop-catalog:8092/metrics", "ciFqdn", "giftshop-catalog.demo")),
                "rules", List.of(Map.of("metric", "up", "invert", true))
        );
        fmModule.returning(List.of(new RemoteSourceConfig(
                SOURCE_ID,
                "giftshop-metrics",
                "stored-hash",
                "active",
                Map.of(),
                "pull_etl",
                "30s",
                parserConfig
        )));

        service.sync();

        SourceConfig stored = sourceConfigs.all().getFirst();
        assertThat(stored.type()).isEqualTo("pull_etl");
        assertThat(stored.schedule()).isEqualTo("30s");
        assertThat(stored.parserConfig()).isEqualTo(parserConfig);
        assertThat(stored.sourceKey()).isEqualTo("giftshop-metrics");
    }

    @Test
    void parserConfigChangeIsPickedUpOnResync() {
        fmModule.returning(List.of(new RemoteSourceConfig(
                SOURCE_ID, "giftshop-metrics", "stored-hash", "active", Map.of(),
                "pull_etl", "30s", Map.of("rules", List.of(Map.of("metric", "up")))
        )));
        service.sync();

        Map<String, Object> updated = Map.of("rules", List.of(Map.of(
                "metric", "process_cpu_usage",
                "thresholds", Map.of("critical", 0.95)
        )));
        fmModule.returning(List.of(new RemoteSourceConfig(
                SOURCE_ID, "giftshop-metrics", "stored-hash", "active", Map.of(),
                "pull_etl", "30s", updated
        )));
        service.sync();

        assertThat(sourceConfigs.all().getFirst().parserConfig()).isEqualTo(updated);
    }

    private static RemoteSourceConfig remote(String status, Map<String, Object> filterRules) {
        return remote(SOURCE_ID, "zabbix-prod-01", status, filterRules);
    }

    private static RemoteSourceConfig remote(
            UUID sourceId,
            String sourceKey,
            String status,
            Map<String, Object> filterRules
    ) {
        return new RemoteSourceConfig(sourceId, sourceKey, "stored-hash", status, filterRules);
    }
}
