package com.wisla.fm.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wisla.kafka")
public record KafkaIngestProperties(
        String rawEventsTopic
) {
}
