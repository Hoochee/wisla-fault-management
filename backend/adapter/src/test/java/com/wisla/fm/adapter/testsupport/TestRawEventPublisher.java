package com.wisla.fm.adapter.testsupport;

import com.wisla.fm.adapter.kafka.RawEventPublisher;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TestRawEventPublisher implements RawEventPublisher {

    private final AtomicReference<PublishResult> nextResult = new AtomicReference<>(PublishResult.ok());
    private final AtomicInteger publishCount = new AtomicInteger();
    private final AtomicReference<UUID> lastSourceId = new AtomicReference<>();
    private final AtomicReference<String> lastSourceKey = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> lastBody = new AtomicReference<>();

    public void stubPublish(PublishResult result) {
        nextResult.set(result);
    }

    public void reset() {
        nextResult.set(PublishResult.ok());
        publishCount.set(0);
        lastSourceId.set(null);
        lastSourceKey.set(null);
        lastBody.set(null);
    }

    public int getPublishCount() {
        return publishCount.get();
    }

    public UUID getLastSourceId() {
        return lastSourceId.get();
    }

    public String getLastSourceKey() {
        return lastSourceKey.get();
    }

    public Map<String, Object> getLastBody() {
        return lastBody.get();
    }

    @Override
    public PublishResult publish(UUID sourceId, String sourceKey, Map<String, Object> ingestBody) {
        publishCount.incrementAndGet();
        lastSourceId.set(sourceId);
        lastSourceKey.set(sourceKey);
        lastBody.set(ingestBody);
        return nextResult.get();
    }
}
