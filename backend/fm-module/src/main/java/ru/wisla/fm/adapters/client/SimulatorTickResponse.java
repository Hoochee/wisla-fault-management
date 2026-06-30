package ru.wisla.fm.adapters.client;

public record SimulatorTickResponse(
        String kind,
        String scenarioId,
        boolean delivered,
        Integer httpStatus,
        String error,
        String note
) {
}
