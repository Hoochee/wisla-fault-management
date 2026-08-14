package ru.wisla.fm.processing.domain;

import java.util.UUID;

public record EventUpdated(UUID eventId, UUID ciId) {
}
