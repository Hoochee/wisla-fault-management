package com.wisla.fm.adapter.ingest.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound port to fm-module, an external system reached over HTTP only. Its read model is the
 * adapter's own; no fm-module Java type crosses this boundary.
 */
public interface FmModuleSourceConfigPort {

    List<RemoteSourceConfig> fetchSources();

    record RemoteSourceConfig(
            UUID sourceId,
            String sourceKey,
            String apiKeyHash,
            String status,
            Map<String, Object> filterRules
    ) {
    }
}
