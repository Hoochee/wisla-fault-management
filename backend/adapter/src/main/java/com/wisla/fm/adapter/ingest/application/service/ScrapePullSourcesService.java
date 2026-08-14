package com.wisla.fm.adapter.ingest.application.service;

import com.wisla.fm.adapter.ingest.application.port.in.ScrapePullSourcesUseCase;
import com.wisla.fm.adapter.ingest.application.port.out.PrometheusScrapePort;
import com.wisla.fm.adapter.ingest.application.port.out.PrometheusScrapePort.Sample;
import com.wisla.fm.adapter.ingest.application.port.out.PrometheusScrapePort.ScrapeResult;
import com.wisla.fm.adapter.ingest.application.port.out.PullMetricStateStorePort;
import com.wisla.fm.adapter.ingest.application.port.out.RawEventPublisherPort;
import com.wisla.fm.adapter.ingest.application.port.out.SourceConfigLookupPort;
import com.wisla.fm.adapter.ingest.domain.MetricThresholdEvaluator;
import com.wisla.fm.adapter.ingest.domain.PullMetricState;
import com.wisla.fm.adapter.ingest.domain.PullTarget;
import com.wisla.fm.adapter.ingest.domain.SourceConfig;
import com.wisla.fm.adapter.ingest.domain.SourceSchedule;
import com.wisla.fm.adapter.ingest.domain.ThresholdRule;
import com.wisla.fm.adapter.ingest.domain.ThresholdRules;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScrapePullSourcesService implements ScrapePullSourcesUseCase {

    private final SourceConfigLookupPort sourceConfigLookup;
    private final PrometheusScrapePort prometheusScrape;
    private final PullMetricStateStorePort metricStates;
    private final RawEventPublisherPort rawEventPublisher;
    private final MetricThresholdEvaluator evaluator;
    private final String adapterVersion;
    private final ConcurrentHashMap<UUID, Instant> lastScrapedAt = new ConcurrentHashMap<>();

    public ScrapePullSourcesService(
            SourceConfigLookupPort sourceConfigLookup,
            PrometheusScrapePort prometheusScrape,
            PullMetricStateStorePort metricStates,
            RawEventPublisherPort rawEventPublisher,
            MetricThresholdEvaluator evaluator,
            String adapterVersion
    ) {
        this.sourceConfigLookup = sourceConfigLookup;
        this.prometheusScrape = prometheusScrape;
        this.metricStates = metricStates;
        this.rawEventPublisher = rawEventPublisher;
        this.evaluator = evaluator;
        this.adapterVersion = adapterVersion;
    }

    @Override
    public void scrapeDue(Instant now) {
        for (SourceConfig config : sourceConfigLookup.findAll()) {
            scrapeSource(config, now);
        }
    }

    private void scrapeSource(SourceConfig config, Instant now) {
        if (config.blocked() || config.ttlExpiresAt().isBefore(now) || !config.isPullEtl()) {
            return;
        }
        Instant lastRun = lastScrapedAt.get(config.sourceId());
        if (!SourceSchedule.isDue(config.schedule(), lastRun, now)) {
            return;
        }
        lastScrapedAt.put(config.sourceId(), now);

        List<ThresholdRule> rules = ThresholdRules.fromParserConfig(config.parserConfig());
        for (PullTarget target : PullTarget.fromParserConfig(config.parserConfig())) {
            scrapeTarget(config, target, rules, now);
        }
    }

    private void scrapeTarget(
            SourceConfig config,
            PullTarget target,
            List<ThresholdRule> rules,
            Instant now
    ) {
        ScrapeResult result = prometheusScrape.scrape(target.url());
        for (ThresholdRule rule : rules) {
            Double value = resolveValue(result, rule);
            if (value == null) {
                continue;
            }
            evaluateAndMaybePublish(config, target, rule, value, now);
        }
    }

    private void evaluateAndMaybePublish(
            SourceConfig config,
            PullTarget target,
            ThresholdRule rule,
            double value,
            Instant now
    ) {
        String severity = evaluator.evaluate(value, rule);
        String externalId = config.sourceKey() + ":" + target.ciFqdn() + ":" + rule.metric();
        String previous = metricStates.find(config.sourceId(), externalId)
                .map(PullMetricState::lastSeverity)
                .orElse(null);
        if (severity.equals(previous)) {
            return;
        }
        boolean firstOk = previous == null && MetricThresholdEvaluator.OK.equals(severity);
        if (!firstOk) {
            rawEventPublisher.publish(
                    config.sourceId(),
                    config.sourceKey(),
                    ingestBody(externalId, target.ciFqdn(), rule.metric(), severity, value, now)
            );
        }
        metricStates.upsert(new PullMetricState(config.sourceId(), externalId, severity, value, now));
    }

    private static Double resolveValue(ScrapeResult result, ThresholdRule rule) {
        if (!result.reachable()) {
            return "up".equals(rule.metric()) ? 0.0 : null;
        }
        for (Sample sample : result.samples()) {
            if (rule.metric().equals(sample.name())) {
                return sample.value();
            }
        }
        if ("up".equals(rule.metric())) {
            return 1.0;
        }
        return null;
    }

    private Map<String, Object> ingestBody(
            String externalId,
            String ciFqdn,
            String metricName,
            String severity,
            double value,
            Instant now
    ) {
        boolean ok = MetricThresholdEvaluator.OK.equals(severity);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("metric", metricName);
        attributes.put("value", value);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("externalId", externalId);
        event.put("title", metricName);
        event.put("severity", ok ? "normal" : severity);
        event.put("status", ok ? "ok" : "problem");
        event.put("nodeFqdn", ciFqdn);
        event.put("occurredAt", now.toString());
        event.put("attributes", attributes);
        event.put("rawPayload", Map.copyOf(attributes));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("events", List.of(event));
        request.put("adapterVersion", adapterVersion);
        request.put("receivedAt", now.toString());
        return request;
    }
}
