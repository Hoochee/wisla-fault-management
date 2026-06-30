package ru.wisla.fm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wisla.service")
public record ServiceProperties(String apiKey) {
}
