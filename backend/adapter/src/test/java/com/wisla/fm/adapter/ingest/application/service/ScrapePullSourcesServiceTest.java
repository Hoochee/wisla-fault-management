package com.wisla.fm.adapter.ingest.application.service;

import com.wisla.fm.adapter.ingest.application.port.out.PrometheusScrapePort.Sample;
import com.wisla.fm.adapter.ingest.domain.FilterRules;
import com.wisla.fm.adapter.ingest.domain.MetricThresholdEvaluator;
import com.wisla.fm.adapter.ingest.domain.PullMetricState;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;
import com.wisla.fm.adapter.ingest.testsupport.FakePrometheusScrapePort;
import com.wisla.fm.adapter.ingest.testsupport.InMemoryPullMetricStateStore;
import com.wisla.fm.adapter.ingest.testsupport.InMemorySourceConfigStore;
import com.wisla.fm.adapter.ingest.testsupport.RecordingRawEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit 5 with outbound-port fakes — no Spring context.
 */
class ScrapePullSourcesServiceTest {

    private static final UUID PULL_SOURCE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PUSH_SOURCE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String SOURCE_KEY = "giftshop-metrics";
    private static final String CI_FQDN = "giftshop-catalog.demo";
    private static final String METRIC = "process_cpu_usage";
    private static final String TARGET_URL = "http://giftshop-catalog:8092/metrics";
    private static final String ADAPTER_VERSION = "1.0.0";
    private static final Instant T0 = Instant.parse("2026-08-13T12:00:00Z");

    private final InMemorySourceConfigStore sources = new InMemorySourceConfigStore();
    private final FakePrometheusScrapePort scrape = new FakePrometheusScrapePort();
    private final InMemoryPullMetricStateStore states = new InMemoryPullMetricStateStore();
    private final RecordingRawEventPublisher publisher = new RecordingRawEventPublisher();

    private ScrapePullSourcesService service;

    @BeforeEach
    void setUp() {
        service = new ScrapePullSourcesService(
                sources,
                scrape,
                states,
                publisher,
                new MetricThresholdEvaluator(),
                ADAPTER_VERSION
        );
        sources.put(pullSource("30s"));
        sources.put(pushSource());
        scrape.returning(TARGET_URL, new Sample(METRIC, 0.90));
    }

    @Test
    void scrapesPullEtlTargetsOnly() {
        service.scrapeDue(T0);

        assertThat(scrape.scrapedUrls()).containsExactly(TARGET_URL);
    }

    @Test
    void crossingAThresholdPublishesProblemWithStableExternalId() {
        service.scrapeDue(T0);

        assertThat(publisher.publishCount()).isEqualTo(1);
        assertThat(publisher.last().sourceId()).isEqualTo(PULL_SOURCE_ID);
        assertThat(publisher.last().sourceKey()).isEqualTo(SOURCE_KEY);

        Map<String, Object> event = firstEvent(publisher.last().ingestBody());
        assertThat(event.get("externalId")).isEqualTo(SOURCE_KEY + ":" + CI_FQDN + ":" + METRIC);
        assertThat(event.get("severity")).isEqualTo("major");
        assertThat(event.get("status")).isEqualTo("problem");
        assertThat(event.get("nodeFqdn")).isEqualTo(CI_FQDN);

        PullMetricState stored = states.find(PULL_SOURCE_ID, SOURCE_KEY + ":" + CI_FQDN + ":" + METRIC).orElseThrow();
        assertThat(stored.lastSeverity()).isEqualTo("major");
        assertThat(stored.lastValue()).isEqualTo(0.90);
    }

    @Test
    void unchangedSeverityDoesNotPublish() {
        service.scrapeDue(T0);
        assertThat(publisher.publishCount()).isEqualTo(1);

        scrape.returning(TARGET_URL, new Sample(METRIC, 0.92));
        service.scrapeDue(T0.plusSeconds(30));

        assertThat(publisher.publishCount()).isEqualTo(1);
    }

    @Test
    void recoveryPublishesOkOnce() {
        service.scrapeDue(T0);
        scrape.returning(TARGET_URL, new Sample(METRIC, 0.10));
        service.scrapeDue(T0.plusSeconds(30));

        assertThat(publisher.publishCount()).isEqualTo(2);
        Map<String, Object> recovery = firstEvent(publisher.published().get(1).ingestBody());
        assertThat(recovery.get("status")).isEqualTo("ok");
        assertThat(recovery.get("externalId")).isEqualTo(SOURCE_KEY + ":" + CI_FQDN + ":" + METRIC);

        scrape.returning(TARGET_URL, new Sample(METRIC, 0.05));
        service.scrapeDue(T0.plusSeconds(60));
        assertThat(publisher.publishCount()).isEqualTo(2);
    }

    @Test
    void publishesThroughRawEventPublisherPortOnly() {
        service.scrapeDue(T0);

        assertThat(publisher.publishCount()).isEqualTo(1);
        assertThat(publisher.last().ingestBody()).containsKeys("events", "adapterVersion", "receivedAt");
        assertThat(publisher.last().ingestBody().get("adapterVersion")).isEqualTo(ADAPTER_VERSION);
    }

    @Test
    void honorsPerSourceIntervalAndSkipsEarlyTicks() {
        service.scrapeDue(T0);
        assertThat(scrape.scrapedUrls()).hasSize(1);

        service.scrapeDue(T0.plusSeconds(29));
        assertThat(scrape.scrapedUrls()).hasSize(1);

        service.scrapeDue(T0.plusSeconds(30));
        assertThat(scrape.scrapedUrls()).hasSize(2);
    }

    @Test
    void invertUpZeroPublishesCritical() {
        sources.put(pullSourceWithUpRule());
        scrape.returning(TARGET_URL, new Sample("up", 0.0));

        service.scrapeDue(T0);

        Map<String, Object> event = firstEvent(publisher.last().ingestBody());
        assertThat(event.get("externalId")).isEqualTo(SOURCE_KEY + ":" + CI_FQDN + ":up");
        assertThat(event.get("severity")).isEqualTo("critical");
        assertThat(event.get("status")).isEqualTo("problem");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstEvent(Map<String, Object> ingestBody) {
        List<Map<String, Object>> events = (List<Map<String, Object>>) ingestBody.get("events");
        return events.getFirst();
    }

    private static SourceConfig pullSource(String schedule) {
        return new SourceConfig(
                PULL_SOURCE_ID,
                SOURCE_KEY,
                "hash",
                "http://fm-module:8080",
                FilterRules.of(Map.of()),
                false,
                T0.plusSeconds(86_400),
                T0,
                T0,
                SourceConfig.TYPE_PULL_ETL,
                schedule,
                parserConfig()
        );
    }

    private static SourceConfig pullSourceWithUpRule() {
        Map<String, Object> parserConfig = new LinkedHashMap<>();
        parserConfig.put("targets", List.of(Map.of("url", TARGET_URL, "ciFqdn", CI_FQDN)));
        parserConfig.put("rules", List.of(Map.of(
                "metric", "up",
                "thresholds", Map.of("critical", 0),
                "invert", true
        )));
        return new SourceConfig(
                PULL_SOURCE_ID,
                SOURCE_KEY,
                "hash",
                "http://fm-module:8080",
                FilterRules.of(Map.of()),
                false,
                T0.plusSeconds(86_400),
                T0,
                T0,
                SourceConfig.TYPE_PULL_ETL,
                "30s",
                parserConfig
        );
    }

    private static SourceConfig pushSource() {
        return new SourceConfig(
                PUSH_SOURCE_ID,
                "zabbix-push",
                "hash",
                "http://fm-module:8080",
                FilterRules.of(Map.of()),
                false,
                T0.plusSeconds(86_400),
                T0,
                T0,
                SourceConfig.TYPE_PUSH_REST,
                null,
                Map.of("targets", List.of(Map.of("url", "http://should-not-scrape/metrics")))
        );
    }

    private static Map<String, Object> parserConfig() {
        Map<String, Object> parserConfig = new LinkedHashMap<>();
        parserConfig.put("targets", List.of(Map.of("url", TARGET_URL, "ciFqdn", CI_FQDN)));
        parserConfig.put("rules", List.of(Map.of(
                "metric", METRIC,
                "thresholds", Map.of("warning", 0.70, "major", 0.85, "critical", 0.95)
        )));
        return parserConfig;
    }
}
