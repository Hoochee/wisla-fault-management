package ru.wisla.fm.admin.api;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ConfigurationItemCreate(
        @NotBlank String fqdn,
        @NotBlank String ciType,
        @NotBlank String system,
        String subsystem,
        String software,
        List<UUID> productIds,
        List<String> tags,
        Map<String, String> externalIds
) {
}
