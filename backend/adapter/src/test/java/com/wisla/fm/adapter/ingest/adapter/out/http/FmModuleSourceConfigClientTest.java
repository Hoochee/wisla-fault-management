package com.wisla.fm.adapter.ingest.adapter.out.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisla.fm.adapter.ingest.application.port.out.FmModuleSourceConfigPort.RemoteSourceConfig;
import com.wisla.fm.adapter.ingest.application.service.SyncSourceConfigService;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;
import com.wisla.fm.adapter.ingest.testsupport.InMemorySourceConfigStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FmModuleSourceConfigClientTest {

    private static final UUID SOURCE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");
    private static final String BASE_URL = "http://fm-module:8080";

    @Test
    void mapsTypeScheduleAndParserConfigFromInternalSourcesJson() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl(BASE_URL).build();
        FmModuleSourceConfigClient client = new FmModuleSourceConfigClient(
                restClient,
                new ObjectMapper(),
                "dev-service-key"
        );

        server.expect(requestTo(BASE_URL + "/api/v1/internal/sources"))
                .andExpect(header("X-Service-Key", "dev-service-key"))
                .andRespond(withSuccess("""
                        [{
                          "sourceId": "%s",
                          "sourceKey": "giftshop-metrics",
                          "apiKeyHash": "stored-hash",
                          "status": "active",
                          "filterRules": {"enabled": true},
                          "type": "pull_etl",
                          "schedule": "30s",
                          "parserConfig": {
                            "targets": [{"url": "http://giftshop-catalog:8092/metrics", "ciFqdn": "giftshop-catalog.demo"}],
                            "rules": [{"metric": "process_cpu_usage", "thresholds": {"warning": 0.70, "major": 0.85, "critical": 0.95}}]
                          }
                        }]
                        """.formatted(SOURCE_ID), MediaType.APPLICATION_JSON));

        List<RemoteSourceConfig> sources = client.fetchSources();

        assertThat(sources).hasSize(1);
        RemoteSourceConfig remote = sources.getFirst();
        assertThat(remote.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(remote.sourceKey()).isEqualTo("giftshop-metrics");
        assertThat(remote.apiKeyHash()).isEqualTo("stored-hash");
        assertThat(remote.status()).isEqualTo("active");
        assertThat(remote.filterRules()).containsEntry("enabled", true);
        assertThat(remote.type()).isEqualTo("pull_etl");
        assertThat(remote.schedule()).isEqualTo("30s");
        assertThat(remote.parserConfig()).containsKey("targets");
        assertThat(remote.parserConfig()).containsKey("rules");
        server.verify();
    }

    @Test
    void missingPullFieldsDefaultSoPushRestStillSyncs() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl(BASE_URL).build();
        FmModuleSourceConfigClient client = new FmModuleSourceConfigClient(
                restClient,
                new ObjectMapper(),
                "dev-service-key"
        );

        server.expect(requestTo(BASE_URL + "/api/v1/internal/sources"))
                .andRespond(withSuccess("""
                        [{
                          "sourceId": "%s",
                          "sourceKey": "zabbix-prod-01",
                          "apiKeyHash": "stored-hash",
                          "status": "active",
                          "filterRules": {}
                        }]
                        """.formatted(SOURCE_ID), MediaType.APPLICATION_JSON));

        RemoteSourceConfig remote = client.fetchSources().getFirst();
        assertThat(remote.type()).isEqualTo("push_rest");
        assertThat(remote.schedule()).isNull();
        assertThat(remote.parserConfig()).isEmpty();
        server.verify();
    }

    @Test
    void syncPersistsPullFieldsOntoSourceConfig() {
        InMemorySourceConfigStore store = new InMemorySourceConfigStore();
        SyncSourceConfigService sync = new SyncSourceConfigService(
                () -> List.of(new RemoteSourceConfig(
                        SOURCE_ID,
                        "giftshop-metrics",
                        "stored-hash",
                        "active",
                        Map.of(),
                        "pull_etl",
                        "30s",
                        Map.of("rules", List.of(Map.of("metric", "up", "invert", true)))
                )),
                store,
                Clock.fixed(NOW, ZoneOffset.UTC),
                BASE_URL
        );

        sync.sync();

        SourceConfig stored = store.all().getFirst();
        assertThat(stored.type()).isEqualTo("pull_etl");
        assertThat(stored.schedule()).isEqualTo("30s");
        assertThat(stored.parserConfig()).containsKey("rules");
        assertThat(stored.sourceKey()).isEqualTo("giftshop-metrics");
    }
}
