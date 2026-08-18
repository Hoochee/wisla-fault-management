package ru.wisla.fm.processing.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface UserDirectoryPort {

    Optional<UserRef> findById(UUID userId);

    record UserRef(UUID id, String fullName, boolean active) {
    }
}
