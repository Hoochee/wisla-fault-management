package ru.wisla.fm.configuration.api;

public record SourceSimulatorControlResult(
        boolean success,
        boolean enabled,
        String message
) {
}
