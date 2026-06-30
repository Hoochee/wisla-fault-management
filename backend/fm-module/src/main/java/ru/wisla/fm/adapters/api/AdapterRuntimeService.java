package ru.wisla.fm.adapters.api;

import org.springframework.stereotype.Service;
import ru.wisla.fm.adapters.client.AdapterHealthClient;
import ru.wisla.fm.adapters.client.AdapterHealthResponse;
import ru.wisla.fm.config.AdapterProperties;
import ru.wisla.fm.configuration.domain.EventSourceEntity;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AdapterRuntimeService {

    static final Duration RECENT_SUCCESS_THRESHOLD = Duration.ofMinutes(15);

    private final AdapterProperties adapterProperties;
    private final AdapterHealthClient adapterHealthClient;
    private final EventSourceRepository eventSourceRepository;

    public AdapterRuntimeService(AdapterProperties adapterProperties,
                                 AdapterHealthClient adapterHealthClient,
                                 EventSourceRepository eventSourceRepository) {
        this.adapterProperties = adapterProperties;
        this.adapterHealthClient = adapterHealthClient;
        this.eventSourceRepository = eventSourceRepository;
    }

    public AdapterRuntimeResponse getRuntime() {
        Optional<AdapterHealthResponse> health = adapterHealthClient.fetchHealth();
        boolean adapterReachable = health.isPresent();
        boolean serviceOk = adapterReachable && "ok".equals(health.get().status());

        AdapterServiceRuntimeDto service = toServiceDto(adapterProperties.baseUrl(), health);
        List<SourceAdapterRuntimeDto> sources = eventSourceRepository.findAll().stream()
                .map(source -> toSourceDto(source, adapterReachable, serviceOk))
                .toList();
        return new AdapterRuntimeResponse(service, sources);
    }

    private AdapterServiceRuntimeDto toServiceDto(String baseUrl, Optional<AdapterHealthResponse> health) {
        if (health.isEmpty()) {
            return new AdapterServiceRuntimeDto("down", null, null, null, null, baseUrl);
        }
        AdapterHealthResponse body = health.get();
        return new AdapterServiceRuntimeDto(
                body.status(),
                body.version(),
                body.database(),
                body.fmModule(),
                body.bufferedCount(),
                baseUrl
        );
    }

    private SourceAdapterRuntimeDto toSourceDto(EventSourceEntity source,
                                                boolean adapterReachable,
                                                boolean serviceOk) {
        return new SourceAdapterRuntimeDto(
                source.getId(),
                source.getName(),
                source.getType(),
                source.getStatus(),
                computeRuntimeStatus(source, adapterReachable, serviceOk),
                source.getAdapterVersion(),
                source.getLastSuccessAt()
        );
    }

    String computeRuntimeStatus(EventSourceEntity source, boolean adapterReachable, boolean serviceOk) {
        String configStatus = source.getStatus();
        if ("inactive".equals(configStatus)) {
            return "stopped";
        }
        if ("blocked".equals(configStatus)) {
            return "degraded";
        }
        if ("active".equals(configStatus)) {
            if (!adapterReachable) {
                return "unreachable";
            }
            if (serviceOk && isRecentSuccess(source.getLastSuccessAt())) {
                return "running";
            }
            if (serviceOk) {
                return "idle";
            }
            return "idle";
        }
        return "idle";
    }

    private boolean isRecentSuccess(Instant lastSuccessAt) {
        if (lastSuccessAt == null) {
            return false;
        }
        return lastSuccessAt.isAfter(Instant.now().minus(RECENT_SUCCESS_THRESHOLD));
    }
}
