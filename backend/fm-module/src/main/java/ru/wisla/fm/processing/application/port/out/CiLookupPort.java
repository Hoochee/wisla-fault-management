package ru.wisla.fm.processing.application.port.out;

import ru.wisla.fm.processing.domain.CiSnapshot;

import java.util.Optional;

/** Resolves the configuration item a raw event's node belongs to, creating it when unknown. */
public interface CiLookupPort {

    Optional<CiSnapshot> findOrCreateByFqdn(String fqdn);
}
