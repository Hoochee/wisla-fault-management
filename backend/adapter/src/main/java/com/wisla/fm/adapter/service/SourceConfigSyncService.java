package com.wisla.fm.adapter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisla.fm.adapter.config.AdapterProperties;
import com.wisla.fm.adapter.persistence.entity.SourceConfigSnapshot;
import com.wisla.fm.adapter.persistence.repository.SourceConfigSnapshotRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SourceConfigSyncService implements ApplicationRunner {

    private final AdapterProperties properties;
    private final SourceConfigSnapshotRepository repository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SourceConfigSyncService(
            AdapterProperties properties,
            SourceConfigSnapshotRepository repository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(properties.fmModuleBaseUrl()).build();
    }

    @Override
    public void run(ApplicationArguments args) {
        syncFromFmModule();
    }

    @Scheduled(fixedDelayString = "${wisla.adapter.config-sync-interval-ms:300000}")
    public void scheduledSync() {
        syncFromFmModule();
    }

    public void syncFromFmModule() {
        try {
            List<Map<String, Object>> sources = restClient.get()
                    .uri("/api/v1/internal/sources")
                    .header("X-Service-Key", properties.fmModuleServiceKey())
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});
            if (sources == null) {
                return;
            }
            Instant now = Instant.now();
            Instant ttl = now.plusSeconds(86400);
            for (Map<String, Object> row : sources) {
                upsert(row, now, ttl);
            }
        } catch (Exception ignored) {
            // fm-module may not be ready on first boot; retry on schedule
        }
    }

    private void upsert(Map<String, Object> row, Instant now, Instant ttl) {
        UUID sourceId = UUID.fromString(String.valueOf(row.get("sourceId")));
        String sourceKey = String.valueOf(row.get("sourceKey"));
        String apiKeyHash = String.valueOf(row.get("apiKeyHash"));
        String status = String.valueOf(row.get("status"));
        Map<String, Object> filterRules = objectMapper.convertValue(
                row.getOrDefault("filterRules", Map.of()),
                new TypeReference<>() {}
        );
        boolean blocked = !"active".equals(status);
        SourceConfigSnapshot snapshot = repository.findById(sourceId).orElseGet(SourceConfigSnapshot::createEmpty);
        snapshot.replace(
                sourceId,
                sourceKey,
                apiKeyHash,
                properties.fmModuleBaseUrl(),
                filterRules,
                blocked,
                ttl,
                now
        );
        repository.save(snapshot);
    }
}
