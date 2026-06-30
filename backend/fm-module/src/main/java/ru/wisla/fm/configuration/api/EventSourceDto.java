package ru.wisla.fm.configuration.api;

import java.time.Instant;
import java.util.UUID;

public record EventSourceDto(
        UUID id,
        String name,
        String type,
        String protocol,
        String endpoint,
        String apiKey,
        String adapterVersion,
        Instant lastSuccessAt,
        String status,
        String schedule
) {
}
