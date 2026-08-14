package ru.wisla.fm.health.application.port.out;

import ru.wisla.fm.health.domain.ActiveSignal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ActiveSignalsPort {

    List<ActiveSignal> findByCiIds(Collection<UUID> ciIds);
}
