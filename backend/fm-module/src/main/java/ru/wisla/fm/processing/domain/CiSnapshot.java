package ru.wisla.fm.processing.domain;

import java.util.UUID;

/** The configuration item fields processing copies onto an event. */
public record CiSnapshot(UUID id, String fqdn, String systemName, String subsystemName) {
}
