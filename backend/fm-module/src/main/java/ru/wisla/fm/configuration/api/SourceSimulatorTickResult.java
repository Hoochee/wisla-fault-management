package ru.wisla.fm.configuration.api;

public record SourceSimulatorTickResult(
        String kind,
        String scenarioId,
        boolean delivered,
        Integer httpStatus,
        String error,
        String note
) {
}
