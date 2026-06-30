package com.wisla.fm.adapter.service;

import com.wisla.fm.adapter.config.AdapterProperties;
import com.wisla.fm.adapter.persistence.entity.BufferedMessage;
import com.wisla.fm.adapter.persistence.repository.BufferedMessageRepository;
import com.wisla.fm.adapter.persistence.repository.SourceConfigSnapshotRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class BufferRetryWorker {

    private final BufferedMessageRepository bufferedMessageRepository;
    private final SourceConfigSnapshotRepository sourceConfigSnapshotRepository;
    private final FmModuleClient fmModuleClient;
    private final IngestPayloadMapper ingestPayloadMapper;
    private final AdapterProperties properties;

    public BufferRetryWorker(
            BufferedMessageRepository bufferedMessageRepository,
            SourceConfigSnapshotRepository sourceConfigSnapshotRepository,
            FmModuleClient fmModuleClient,
            IngestPayloadMapper ingestPayloadMapper,
            AdapterProperties properties
    ) {
        this.bufferedMessageRepository = bufferedMessageRepository;
        this.sourceConfigSnapshotRepository = sourceConfigSnapshotRepository;
        this.fmModuleClient = fmModuleClient;
        this.ingestPayloadMapper = ingestPayloadMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${wisla.adapter.buffer-retry-interval-ms:60000}")
    @Transactional
    public void retryBufferedMessages() {
        List<BufferedMessage> ready = bufferedMessageRepository.findReadyForRetry(Instant.now());
        for (BufferedMessage message : ready) {
            retryMessage(message);
        }
    }

    private void retryMessage(BufferedMessage message) {
        var configOpt = sourceConfigSnapshotRepository.findById(message.getSourceId());
        if (configOpt.isEmpty()) {
            message.scheduleRetry(properties.bufferRetryBaseSeconds());
            bufferedMessageRepository.save(message);
            return;
        }

        String ingestApiKey = message.getIngestApiKey();
        if (ingestApiKey == null || ingestApiKey.isBlank()) {
            message.scheduleRetry(properties.bufferRetryBaseSeconds());
            bufferedMessageRepository.save(message);
            return;
        }

        var config = configOpt.get();
        var ingestBody = ingestPayloadMapper.toIngestRequest(message.getPayload());
        FmModuleClient.IngestResult result = fmModuleClient.forwardIngest(
                config.getEndpoint(),
                ingestApiKey,
                ingestBody
        );

        if (result.success()) {
            bufferedMessageRepository.delete(message);
            return;
        }

        if (!result.retryable()) {
            bufferedMessageRepository.delete(message);
            return;
        }

        message.scheduleRetry(properties.bufferRetryBaseSeconds());
        bufferedMessageRepository.save(message);
    }
}
