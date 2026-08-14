package ru.wisla.fm.health.domain;

public record HealthCalculation(int healthPercent, int damagePercent, SnapshotPayload payload) {
}
