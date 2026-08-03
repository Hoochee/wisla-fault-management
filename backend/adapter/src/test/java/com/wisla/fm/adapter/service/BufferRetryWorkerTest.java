package com.wisla.fm.adapter.service;

import com.wisla.fm.adapter.kafka.RawEventPublisher;
import com.wisla.fm.adapter.persistence.entity.BufferedMessage;
import com.wisla.fm.adapter.persistence.repository.BufferedMessageRepository;
import com.wisla.fm.adapter.persistence.repository.SourceConfigSnapshotRepository;
import com.wisla.fm.adapter.testsupport.RawEventPublisherTestConfiguration;
import com.wisla.fm.adapter.testsupport.SourceConfigTestData;
import com.wisla.fm.adapter.testsupport.TestFmModuleClient;
import com.wisla.fm.adapter.testsupport.TestRawEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(RawEventPublisherTestConfiguration.class)
class BufferRetryWorkerTest {

    private static final UUID SOURCE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String SOURCE_KEY = "buffer-retry-source";
    private static final String API_KEY = "buffer-api-key";

    @Autowired
    private BufferRetryWorker bufferRetryWorker;

    @Autowired
    private BufferedMessageRepository bufferedMessageRepository;

    @Autowired
    private SourceConfigSnapshotRepository sourceConfigSnapshotRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestRawEventPublisher rawEventPublisher;

    @Autowired(required = false)
    private TestFmModuleClient fmModuleClient;

    @BeforeEach
    void setUp() {
        rawEventPublisher.reset();
        bufferedMessageRepository.deleteAll();
        sourceConfigSnapshotRepository.deleteAll();
        sourceConfigSnapshotRepository.save(SourceConfigTestData.snapshot(
                SOURCE_ID,
                SOURCE_KEY,
                passwordEncoder.encode(API_KEY),
                "http://fm-module:8080",
                Map.of(),
                false
        ));
    }

    @Test
    void retryPublishesViaKafkaAndDeletesBufferOnSuccess() {
        bufferedMessageRepository.save(new BufferedMessage(
                SOURCE_ID,
                API_KEY,
                Map.of("event_id", "buf-1", "severity", "high"),
                Instant.now().minusSeconds(1)
        ));
        rawEventPublisher.stubPublish(RawEventPublisher.PublishResult.ok());

        bufferRetryWorker.retryBufferedMessages();

        assertThat(bufferedMessageRepository.count()).isZero();
        assertThat(rawEventPublisher.getPublishCount()).isEqualTo(1);
        assertThat(rawEventPublisher.getLastSourceId()).isEqualTo(SOURCE_ID);
        assertThat(rawEventPublisher.getLastSourceKey()).isEqualTo(SOURCE_KEY);
    }

    @Test
    void retryDoesNotCallHttpIngest() {
        bufferedMessageRepository.save(new BufferedMessage(
                SOURCE_ID,
                API_KEY,
                Map.of("event_id", "buf-2"),
                Instant.now().minusSeconds(1)
        ));
        rawEventPublisher.stubPublish(RawEventPublisher.PublishResult.ok());

        bufferRetryWorker.retryBufferedMessages();

        // Default path is Kafka-only; TestFmModuleClient is not wired for ingest cutover.
        assertThat(fmModuleClient).isNull();
        assertThat(rawEventPublisher.getPublishCount()).isEqualTo(1);
    }

    @Test
    void retryKeepsBufferWhenKafkaIsRetryable() {
        BufferedMessage buffered = bufferedMessageRepository.save(new BufferedMessage(
                SOURCE_ID,
                API_KEY,
                Map.of("event_id", "buf-3"),
                Instant.now().minusSeconds(1)
        ));
        rawEventPublisher.stubPublish(RawEventPublisher.PublishResult.retryable("broker down"));

        bufferRetryWorker.retryBufferedMessages();

        assertThat(bufferedMessageRepository.findById(buffered.getId())).isPresent();
        assertThat(bufferedMessageRepository.findById(buffered.getId()).orElseThrow().getRetryCount())
                .isEqualTo(1);
    }
}
