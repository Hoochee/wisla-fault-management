package com.wisla.fm.adapter.ingest.adapter.out.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisla.fm.adapter.ingest.application.port.out.FmModuleSourceConfigPort;
import com.wisla.fm.adapter.ingest.infrastructure.config.AdapterProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the source configuration owned by fm-module over HTTP only, mapping the response into this
 * service's own read model. Extra JSON keys ({@code type}, {@code schedule}, {@code parserConfig})
 * are deserialized additively so this client works before fm-module expands its DTO.
 */
@Component
public class FmModuleSourceConfigClient implements FmModuleSourceConfigPort {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String serviceKey;

    @Autowired
    public FmModuleSourceConfigClient(AdapterProperties properties, ObjectMapper objectMapper) {
        this(RestClient.builder().baseUrl(properties.fmModuleBaseUrl()).build(), objectMapper, properties.fmModuleServiceKey());
    }

    FmModuleSourceConfigClient(RestClient restClient, ObjectMapper objectMapper, String serviceKey) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.serviceKey = serviceKey;
    }

    @Override
    public List<RemoteSourceConfig> fetchSources() {
        List<Map<String, Object>> rows = restClient.get()
                .uri("/api/v1/internal/sources")
                .header("X-Service-Key", serviceKey)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (rows == null) {
            return null;
        }
        return rows.stream().map(this::toRemoteSourceConfig).toList();
    }

    private RemoteSourceConfig toRemoteSourceConfig(Map<String, Object> row) {
        return new RemoteSourceConfig(
                UUID.fromString(String.valueOf(row.get("sourceId"))),
                String.valueOf(row.get("sourceKey")),
                String.valueOf(row.get("apiKeyHash")),
                String.valueOf(row.get("status")),
                convertMap(row.get("filterRules")),
                stringOrNull(row.get("type")),
                stringOrNull(row.get("schedule")),
                convertMap(row.get("parserConfig"))
        );
    }

    private Map<String, Object> convertMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return objectMapper.convertValue(value, new TypeReference<>() {});
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() || "null".equals(text) ? null : text;
    }
}
