package ru.wisla.fm.cmdb.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.wisla.fm.cmdb.domain.ConfigurationItemEntity;

import java.util.Optional;
import java.util.UUID;

public interface ConfigurationItemRepository extends JpaRepository<ConfigurationItemEntity, UUID>,
        JpaSpecificationExecutor<ConfigurationItemEntity> {

    Optional<ConfigurationItemEntity> findByFqdn(String fqdn);

    boolean existsByFqdn(String fqdn);
}
