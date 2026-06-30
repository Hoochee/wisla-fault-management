package ru.wisla.fm.configuration.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_sources")
public class EventSourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 64)
    private String protocol;

    @Column(nullable = false, length = 512)
    private String endpoint;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(name = "api_key_prefix", nullable = false, length = 16)
    private String apiKeyPrefix;

    @Column(nullable = false, length = 16)
    private String status = "inactive";

    private String schedule;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_rules", columnDefinition = "jsonb", nullable = false)
    private String filterRules = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parser_config", columnDefinition = "jsonb", nullable = false)
    private String parserConfig = "{}";

    @Column(name = "adapter_version", length = 32)
    private String adapterVersion;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "webhook_path_key", unique = true, length = 64)
    private String webhookPathKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public void setApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
    }

    public String getApiKeyPrefix() {
        return apiKeyPrefix;
    }

    public void setApiKeyPrefix(String apiKeyPrefix) {
        this.apiKeyPrefix = apiKeyPrefix;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }

    public String getFilterRules() {
        return filterRules;
    }

    public void setFilterRules(String filterRules) {
        this.filterRules = filterRules;
    }

    public String getParserConfig() {
        return parserConfig;
    }

    public void setParserConfig(String parserConfig) {
        this.parserConfig = parserConfig;
    }

    public String getAdapterVersion() {
        return adapterVersion;
    }

    public void setAdapterVersion(String adapterVersion) {
        this.adapterVersion = adapterVersion;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(Instant lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }

    public String getWebhookPathKey() {
        return webhookPathKey;
    }

    public void setWebhookPathKey(String webhookPathKey) {
        this.webhookPathKey = webhookPathKey;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
