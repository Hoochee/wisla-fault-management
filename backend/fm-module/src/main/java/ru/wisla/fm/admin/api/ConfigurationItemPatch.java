package ru.wisla.fm.admin.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ConfigurationItemPatch(
        String fqdn,
        String ciType,
        String system,
        String subsystem,
        String software,
        List<UUID> productIds,
        List<String> tags,
        Map<String, String> externalIds
) {
}
