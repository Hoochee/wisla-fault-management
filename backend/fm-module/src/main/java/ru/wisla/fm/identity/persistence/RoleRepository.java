package ru.wisla.fm.identity.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wisla.fm.identity.domain.RoleEntity;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    boolean existsByName(String name);

    Optional<RoleEntity> findByName(String name);
}
