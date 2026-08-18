package ru.wisla.fm.processing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A journal row for a duty action. {@code comment} is a domain field; JSON metadata is written by
 * the persistence adapter.
 */
public record ActionLogEntry(
        UUID id,
        UUID eventId,
        String action,
        UUID userId,
        String userName,
        String details,
        String comment,
        Instant createdAt
) {

    public ActionLogEntry withPersisted(UUID persistedId, Instant persistedAt) {
        return new ActionLogEntry(
                persistedId, eventId, action, userId, userName, details, comment, persistedAt);
    }
}
