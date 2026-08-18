package ru.wisla.fm.processing.application.port.in;

import java.util.UUID;

/**
 * Duty action requested by an authenticated operator. Free of Spring {@code Authentication}
 * and HTTP types (ADR-001).
 */
public record EventActionCommand(
        UUID eventId,
        String action,
        UUID actorUserId,
        String comment,
        UUID assignedUserId,
        Integer silenceMinutes
) {
}
