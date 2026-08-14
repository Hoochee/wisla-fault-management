package com.wisla.fm.adapter.ingest.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wisla.fm.adapter.ingest.application.port.out.ApiKeyVerifierPort;
import com.wisla.fm.adapter.ingest.application.port.out.BufferedEventStorePort;
import com.wisla.fm.adapter.ingest.application.port.out.FmModuleSourceConfigPort;
import com.wisla.fm.adapter.ingest.application.port.out.PrometheusScrapePort;
import com.wisla.fm.adapter.ingest.application.port.out.PullMetricStateStorePort;
import com.wisla.fm.adapter.ingest.application.port.out.RawEventPublisherPort;
import com.wisla.fm.adapter.ingest.application.port.out.SourceConfigLookupPort;
import com.wisla.fm.adapter.ingest.application.port.out.SourceConfigStorePort;
import com.wisla.fm.adapter.ingest.application.service.ReceiveWebhookEventService;
import com.wisla.fm.adapter.ingest.application.service.RetryBufferedEventsService;
import com.wisla.fm.adapter.ingest.application.service.ScrapePullSourcesService;
import com.wisla.fm.adapter.ingest.application.service.SyncSourceConfigService;
import com.wisla.fm.adapter.ingest.domain.MetricThresholdEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    ObjectMapper objectMapper() {
        // Register only JavaTimeModule. findAndRegisterModules() can pick up Jackson's Scala
        // module from spring-kafka-test / Kafka on the classpath and break java.util.Map JSON.
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ReceiveWebhookEventService receiveWebhookEventService(
            SourceConfigLookupPort sourceConfigLookup,
            BufferedEventStorePort bufferedEventStore,
            RawEventPublisherPort rawEventPublisher,
            ApiKeyVerifierPort apiKeyVerifier,
            Clock clock,
            AdapterProperties properties
    ) {
        return new ReceiveWebhookEventService(
                sourceConfigLookup,
                bufferedEventStore,
                rawEventPublisher,
                apiKeyVerifier,
                clock,
                properties.version(),
                properties.bufferRetryBaseSeconds()
        );
    }

    @Bean
    RetryBufferedEventsService retryBufferedEventsService(
            SourceConfigLookupPort sourceConfigLookup,
            BufferedEventStorePort bufferedEventStore,
            RawEventPublisherPort rawEventPublisher,
            Clock clock,
            AdapterProperties properties
    ) {
        return new RetryBufferedEventsService(
                sourceConfigLookup,
                bufferedEventStore,
                rawEventPublisher,
                clock,
                properties.version(),
                properties.bufferRetryBaseSeconds()
        );
    }

    @Bean
    SyncSourceConfigService syncSourceConfigService(
            FmModuleSourceConfigPort fmModuleSourceConfig,
            SourceConfigStorePort sourceConfigStore,
            Clock clock,
            AdapterProperties properties
    ) {
        return new SyncSourceConfigService(
                fmModuleSourceConfig,
                sourceConfigStore,
                clock,
                properties.fmModuleBaseUrl()
        );
    }

    @Bean
    MetricThresholdEvaluator metricThresholdEvaluator() {
        return new MetricThresholdEvaluator();
    }

    @Bean
    ScrapePullSourcesService scrapePullSourcesService(
            SourceConfigLookupPort sourceConfigLookup,
            PrometheusScrapePort prometheusScrape,
            PullMetricStateStorePort pullMetricStates,
            RawEventPublisherPort rawEventPublisher,
            MetricThresholdEvaluator metricThresholdEvaluator,
            AdapterProperties properties
    ) {
        return new ScrapePullSourcesService(
                sourceConfigLookup,
                prometheusScrape,
                pullMetricStates,
                rawEventPublisher,
                metricThresholdEvaluator,
                properties.version()
        );
    }
}
