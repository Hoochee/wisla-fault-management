package ru.wisla.fm.processing.adapter.out.identity;

import org.springframework.stereotype.Component;
import ru.wisla.fm.identity.persistence.UserRepository;
import ru.wisla.fm.processing.application.port.out.UserDirectoryPort;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserDirectoryAdapter implements UserDirectoryPort {

    private final UserRepository userRepository;

    public UserDirectoryAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserRef> findById(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> new UserRef(user.getId(), user.getFullName(), user.isActive()));
    }
}
