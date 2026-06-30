package ru.wisla.fm.cmdb.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.cmdb.domain.ConfigurationItemEntity;
import ru.wisla.fm.cmdb.persistence.ConfigurationItemRepository;

import java.util.Optional;
import java.util.UUID;

@Service
public class CiService {

    private final ConfigurationItemRepository configurationItemRepository;

    public CiService(ConfigurationItemRepository configurationItemRepository) {
        this.configurationItemRepository = configurationItemRepository;
    }

    @Transactional
    public Optional<ConfigurationItemEntity> findOrCreateByFqdn(String fqdn) {
        if (fqdn == null || fqdn.isBlank()) {
            return Optional.empty();
        }
        String normalized = fqdn.trim().toLowerCase();
        return configurationItemRepository.findByFqdn(normalized)
                .or(() -> Optional.of(configurationItemRepository.save(createAutoCi(normalized))));
    }

    private ConfigurationItemEntity createAutoCi(String fqdn) {
        ConfigurationItemEntity ci = new ConfigurationItemEntity();
        ci.setFqdn(fqdn);
        ci.setCiType("node");
        ci.setSystemName(extractSystemName(fqdn));
        ci.setAutoCreated(true);
        return ci;
    }

    private String extractSystemName(String fqdn) {
        int dot = fqdn.indexOf('.');
        return dot > 0 ? fqdn.substring(0, dot) : fqdn;
    }

    public Optional<ConfigurationItemEntity> findById(UUID id) {
        return configurationItemRepository.findById(id);
    }
}
