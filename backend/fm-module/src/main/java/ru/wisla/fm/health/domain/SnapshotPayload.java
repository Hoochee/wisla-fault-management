package ru.wisla.fm.health.domain;

import java.util.List;

public record SnapshotPayload(
        List<ComponentHealth> components,
        List<SignalContribution> signals,
        Sankey sankey
) {
}
