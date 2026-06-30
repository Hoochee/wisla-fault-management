package ru.wisla.fm.adapters.api;

public record AdapterServiceRuntimeDto(
        String status,
        String version,
        String database,
        String fmModule,
        Long bufferedMessages,
        String baseUrl
) {
}
