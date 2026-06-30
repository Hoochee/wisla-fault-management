package ru.wisla.fm.adapters.api;

import java.time.Instant;
import java.util.UUID;

public record SourceAdapterRuntimeDto(
        UUID sourceId,
        String name,
        String type,
        String configStatus,
        String adapterRuntimeStatus,
        String adapterVersion,
        Instant lastSuccessAt
) {
}
