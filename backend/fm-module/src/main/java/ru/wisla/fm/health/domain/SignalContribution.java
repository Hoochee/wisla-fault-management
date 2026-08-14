package ru.wisla.fm.health.domain;

import java.util.UUID;

public record SignalContribution(UUID ciId, String severity, int healthPercent) {
}
