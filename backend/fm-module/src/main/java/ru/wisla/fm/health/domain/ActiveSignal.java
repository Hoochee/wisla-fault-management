package ru.wisla.fm.health.domain;

import java.util.UUID;

public record ActiveSignal(UUID eventId, UUID ciId, String severity, String title) {
}
