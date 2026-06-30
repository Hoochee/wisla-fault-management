package ru.wisla.fm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wisla.jwt")
public record JwtProperties(String secret, long expirationSeconds) {
}
