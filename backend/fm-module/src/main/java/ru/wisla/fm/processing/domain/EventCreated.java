package ru.wisla.fm.processing.domain;

import java.util.UUID;

public record EventCreated(UUID eventId, UUID ciId) {
}
