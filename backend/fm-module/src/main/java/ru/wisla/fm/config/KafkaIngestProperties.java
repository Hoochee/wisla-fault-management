package ru.wisla.fm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wisla.kafka")
public record KafkaIngestProperties(String rawEventsTopic) {
}
