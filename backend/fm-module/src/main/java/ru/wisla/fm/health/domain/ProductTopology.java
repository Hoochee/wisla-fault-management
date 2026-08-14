package ru.wisla.fm.health.domain;

import java.util.List;
import java.util.UUID;

public record ProductTopology(
        UUID productId,
        String name,
        String tenant,
        String site,
        List<String> tags,
        List<UUID> ciIds,
        List<ComponentNode> components
) {
}
