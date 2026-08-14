package ru.wisla.fm.processing.domain;

import java.util.UUID;

public record EventClosed(UUID eventId, UUID ciId) {
}
