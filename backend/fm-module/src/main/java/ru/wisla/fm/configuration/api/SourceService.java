package ru.wisla.fm.configuration.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.adapters.client.AdapterInternalClient;
import ru.wisla.fm.adapters.client.AdapterProbeRequest;
import ru.wisla.fm.adapters.client.AdapterProbeResponse;
import ru.wisla.fm.adapters.client.SimulatorControlResponse;
import ru.wisla.fm.adapters.client.SimulatorHealthResponse;
import ru.wisla.fm.adapters.client.SimulatorRuntimeConfigRequest;
import ru.wisla.fm.adapters.client.SimulatorRuntimeConfigResponse;
import ru.wisla.fm.adapters.client.SimulatorTickResponse;
import ru.wisla.fm.adapters.client.ZabbixSimulatorClient;
import ru.wisla.fm.common.api.NotFoundException;
import ru.wisla.fm.config.AdapterProperties;
import ru.wisla.fm.config.ZabbixSimulatorProperties;
import ru.wisla.fm.configuration.domain.EventSourceEntity;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SourceService {

    private static final Logger log = LoggerFactory.getLogger(SourceService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EventSourceRepository eventSourceRepository;
    private final EventJpaRepository eventRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final AdapterProperties adapterProperties;
    private final ZabbixSimulatorProperties zabbixSimulatorProperties;
    private final AdapterInternalClient adapterInternalClient;
    private final ZabbixSimulatorClient zabbixSimulatorClient;

    public SourceService(EventSourceRepository eventSourceRepository,
                         EventJpaRepository eventRepository,
                         PasswordEncoder passwordEncoder,
                         ObjectMapper objectMapper,
                         AdapterProperties adapterProperties,
                         ZabbixSimulatorProperties zabbixSimulatorProperties,
                         AdapterInternalClient adapterInternalClient,
                         ZabbixSimulatorClient zabbixSimulatorClient) {
        this.eventSourceRepository = eventSourceRepository;
        this.eventRepository = eventRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.adapterProperties = adapterProperties;
        this.zabbixSimulatorProperties = zabbixSimulatorProperties;
        this.adapterInternalClient = adapterInternalClient;
        this.zabbixSimulatorClient = zabbixSimulatorClient;
    }

    public List<EventSourceDto> listSources(String status, String type, String search) {
        return eventSourceRepository.findAll().stream()
                .filter(source -> status == null || status.equals(source.getStatus()))
                .filter(source -> type == null || type.equals(source.getType()))
                .filter(source -> search == null || source.getName().toLowerCase().contains(search.toLowerCase()))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public EventSourceDto createSource(EventSourceCreate request) {
        String apiKey = generateApiKey();
        EventSourceEntity entity = new EventSourceEntity();
        entity.setName(request.name());
        entity.setType(request.type());
        entity.setProtocol(request.protocol());
        entity.setEndpoint(request.endpoint());
        entity.setSchedule(request.schedule());
        entity.setStatus(request.status() != null ? request.status() : "inactive");
        entity.setApiKeyHash(passwordEncoder.encode(apiKey));
        entity.setApiKeyPrefix(maskApiKey(apiKey));
        entity.setWebhookPathKey(generateWebhookKey());
        EventSourceEntity saved = eventSourceRepository.save(entity);
        syncAdapterConfigBestEffort();
        return toDtoWithKey(saved, apiKey);
    }

    public EventSourceDetailDto getSource(UUID id) {
        EventSourceEntity entity = findOrThrow(id);
        return new EventSourceDetailDto(
                toDto(entity),
                parseJson(entity.getFilterRules()),
                parseJson(entity.getParserConfig()),
                buildWebhookUrl(entity)
        );
    }

    @Transactional
    public EventSourceDto patchSource(UUID id, EventSourcePatch patch) {
        EventSourceEntity entity = findOrThrow(id);
        if (patch.name() != null) {
            entity.setName(patch.name());
        }
        if (patch.protocol() != null) {
            entity.setProtocol(patch.protocol());
        }
        if (patch.endpoint() != null) {
            entity.setEndpoint(patch.endpoint());
        }
        if (patch.schedule() != null) {
            entity.setSchedule(patch.schedule());
        }
        if (patch.status() != null) {
            entity.setStatus(patch.status());
        }
        if (patch.filterRules() != null) {
            entity.setFilterRules(toJson(patch.filterRules()));
        }
        if (patch.parserConfig() != null) {
            entity.setParserConfig(toJson(patch.parserConfig()));
        }
        if (Boolean.TRUE.equals(patch.regenerateApiKey())) {
            String apiKey = generateApiKey();
            entity.setApiKeyHash(passwordEncoder.encode(apiKey));
            entity.setApiKeyPrefix(maskApiKey(apiKey));
            EventSourceEntity saved = eventSourceRepository.save(entity);
            syncAdapterConfigBestEffort();
            return toDtoWithKey(saved, apiKey);
        }
        EventSourceEntity saved = eventSourceRepository.save(entity);
        syncAdapterConfigBestEffort();
        return toDto(saved);
    }

    @Transactional
    public void deleteSource(UUID id) {
        EventSourceEntity entity = findOrThrow(id);
        if (eventRepository.existsBySourceId(id)) {
            throw new IllegalStateException("Source has dependent events");
        }
        eventSourceRepository.delete(entity);
    }

    public SourceTestResult testSource(UUID id, String ingestApiKey) {
        EventSourceEntity entity = findOrThrow(id);
        syncAdapterConfigBestEffort();
        AdapterProbeResponse probe = adapterInternalClient.probe(
                new AdapterProbeRequest(id, ingestApiKey, null)
        );
        String message = probe.success()
                ? "Adapter probe delivered to fm-module"
                : (probe.error() != null ? probe.error() : "Adapter probe failed");
        return new SourceTestResult(
                probe.success(),
                message,
                probe.probedAt() != null ? probe.probedAt() : Instant.now(),
                probe.sourceId() != null ? probe.sourceId() : entity.getId(),
                probe.delivery(),
                probe.latencyMs()
        );
    }

    public BindSimulatorResponse bindSimulator(UUID id, String ingestApiKey) {
        EventSourceEntity entity = findOrThrow(id);
        syncAdapterConfigBestEffort();
        String webhookKey = requireWebhookKey(entity);
        SimulatorRuntimeConfigResponse config = zabbixSimulatorClient.applyConfig(
                new SimulatorRuntimeConfigRequest(webhookKey, ingestApiKey)
        );
        syncAdapterConfigBestEffort();
        String adapterWebhookUrl = config.adapterWebhookUrl() != null
                ? config.adapterWebhookUrl()
                : buildWebhookUrl(entity);
        return new BindSimulatorResponse(
                config.success(),
                config.message() != null ? config.message() : "Simulator configured",
                webhookKey,
                adapterWebhookUrl,
                config.enabled()
        );
    }

    public SourceSimulatorStatus getSimulatorStatus(UUID id) {
        EventSourceEntity entity = findOrThrow(id);
        String webhookKey = entity.getWebhookPathKey();
        SimulatorHealthResponse health = zabbixSimulatorClient.fetchHealth();
        boolean bound = webhookKey != null && webhookKey.equals(health.sourceWebhookKey());
        return new SourceSimulatorStatus(
                true,
                bound,
                health.enabled(),
                webhookKey,
                health.adapterWebhookUrl(),
                zabbixSimulatorProperties.baseUrl(),
                health.activeProblems(),
                bound ? "Simulator bound to this source" : "Simulator not bound to this source"
        );
    }

    public SourceSimulatorTickResult sendTestEvent(UUID id) {
        EventSourceEntity entity = findOrThrow(id);
        syncAdapterConfigBestEffort();
        SourceSimulatorStatus status = getSimulatorStatus(id);
        if (!status.bound()) {
            throw new IllegalStateException(
                    "Simulator is not bound to source " + entity.getWebhookPathKey()
            );
        }
        SimulatorTickResponse tick = zabbixSimulatorClient.tick();
        return new SourceSimulatorTickResult(
                tick.kind(),
                tick.scenarioId(),
                tick.delivered(),
                tick.httpStatus(),
                tick.error(),
                tick.note()
        );
    }

    public SourceSimulatorControlResult setSimulatorControl(UUID id, boolean enabled) {
        findOrThrow(id);
        SimulatorControlResponse control = zabbixSimulatorClient.setControl(enabled);
        return new SourceSimulatorControlResult(
                true,
                control.enabled(),
                control.message()
        );
    }

    public InternalSourceConfigDto getInternalConfig(UUID id) {
        EventSourceEntity entity = findOrThrow(id);
        return new InternalSourceConfigDto(
                entity.getId(),
                entity.getApiKeyHash(),
                entity.getStatus(),
                parseJson(entity.getFilterRules()),
                entity.getEndpoint(),
                entity.getType(),
                entity.getUpdatedAt()
        );
    }

    public List<InternalSourceIndexDto> listInternalSources() {
        return eventSourceRepository.findAll().stream()
                .map(entity -> new InternalSourceIndexDto(
                        entity.getId(),
                        entity.getWebhookPathKey(),
                        entity.getApiKeyHash(),
                        entity.getStatus(),
                        parseJson(entity.getFilterRules())
                ))
                .toList();
    }

    private void syncAdapterConfigBestEffort() {
        try {
            adapterInternalClient.syncConfig();
        } catch (Exception ex) {
            log.warn("Adapter config sync failed after source mutation: {}", ex.getMessage());
        }
    }

    private EventSourceEntity findOrThrow(UUID id) {
        return eventSourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Source not found"));
    }

    private String requireWebhookKey(EventSourceEntity entity) {
        String webhookKey = entity.getWebhookPathKey();
        if (webhookKey == null || webhookKey.isBlank()) {
            throw new IllegalArgumentException("Source has no webhook path key");
        }
        return webhookKey;
    }

    private EventSourceDto toDto(EventSourceEntity entity) {
        return new EventSourceDto(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getProtocol(),
                entity.getEndpoint(),
                entity.getApiKeyPrefix(),
                entity.getAdapterVersion(),
                entity.getLastSuccessAt(),
                entity.getStatus(),
                entity.getSchedule()
        );
    }

    private EventSourceDto toDtoWithKey(EventSourceEntity entity, String apiKey) {
        return new EventSourceDto(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getProtocol(),
                entity.getEndpoint(),
                apiKey,
                entity.getAdapterVersion(),
                entity.getLastSuccessAt(),
                entity.getStatus(),
                entity.getSchedule()
        );
    }

    private String buildWebhookUrl(EventSourceEntity entity) {
        if (entity.getWebhookPathKey() == null) {
            return null;
        }
        return adapterProperties.effectivePublicUrl() + "/webhook/" + entity.getWebhookPathKey();
    }

    private String generateApiKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return "zk-" + HexFormat.of().formatHex(bytes).substring(0, 8) + "-mos-key";
    }

    private String generateWebhookKey() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String maskApiKey(String apiKey) {
        if (apiKey.length() <= 8) {
            return apiKey;
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
