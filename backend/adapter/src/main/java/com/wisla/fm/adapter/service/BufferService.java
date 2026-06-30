package com.wisla.fm.adapter.service;

import com.wisla.fm.adapter.config.AdapterProperties;
import com.wisla.fm.adapter.persistence.entity.BufferedMessage;
import com.wisla.fm.adapter.persistence.repository.BufferedMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class BufferService {

    private final BufferedMessageRepository repository;
    private final AdapterProperties properties;

    public BufferService(BufferedMessageRepository repository, AdapterProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public BufferedMessage buffer(UUID sourceId, String ingestApiKey, Map<String, Object> payload) {
        Instant nextRetry = Instant.now().plusSeconds(properties.bufferRetryBaseSeconds());
        BufferedMessage message = new BufferedMessage(sourceId, ingestApiKey, payload, nextRetry);
        return repository.save(message);
    }

    public long countBuffered() {
        return repository.count();
    }
}
