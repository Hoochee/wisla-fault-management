package com.wisla.fm.adapter.service;

import com.wisla.fm.adapter.persistence.entity.SourceConfigSnapshot;
import com.wisla.fm.adapter.persistence.repository.SourceConfigSnapshotRepository;
import com.wisla.fm.adapter.web.dto.SourceConfigSnapshotDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SourceConfigService {

    private final SourceConfigSnapshotRepository repository;

    public SourceConfigService(SourceConfigSnapshotRepository repository) {
        this.repository = repository;
    }

    public SourceConfigSnapshot requireBySourceKey(String sourceKey) {
        return repository.findBySourceKey(sourceKey)
                .filter(snapshot -> !snapshot.isExpired())
                .orElseThrow(() -> new AdapterException(
                        "unknown_source",
                        "Source configuration not found or expired for key: " + sourceKey,
                        404
                ));
    }

    public SourceConfigSnapshot requireBySourceId(UUID sourceId) {
        return repository.findById(sourceId)
                .filter(snapshot -> !snapshot.isExpired())
                .orElseThrow(() -> new AdapterException(
                        "config_not_found",
                        "Source configuration snapshot not found or expired",
                        404
                ));
    }

    public SourceConfigSnapshotDto toDto(SourceConfigSnapshot snapshot) {
        return new SourceConfigSnapshotDto(
                snapshot.getSourceId(),
                snapshot.getSourceKey(),
                snapshot.getFilterRules(),
                snapshot.getApiKeyHash(),
                snapshot.getEndpoint(),
                snapshot.isBlocked(),
                snapshot.getTtlExpiresAt(),
                snapshot.getUpdatedAt()
        );
    }
}
