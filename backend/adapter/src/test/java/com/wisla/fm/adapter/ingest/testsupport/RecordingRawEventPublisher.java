package com.wisla.fm.adapter.ingest.testsupport;

import com.wisla.fm.adapter.ingest.application.port.out.RawEventPublisherPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecordingRawEventPublisher implements RawEventPublisherPort {

    private final List<Published> published = new ArrayList<>();
    private PublishResult nextResult = PublishResult.ok();

    public void stub(PublishResult result) {
        this.nextResult = result;
    }

    public List<Published> published() {
        return List.copyOf(published);
    }

    public Published last() {
        return published.getLast();
    }

    public int publishCount() {
        return published.size();
    }

    @Override
    public PublishResult publish(UUID sourceId, String sourceKey, Map<String, Object> ingestBody) {
        published.add(new Published(sourceId, sourceKey, ingestBody));
        return nextResult;
    }

    public record Published(UUID sourceId, String sourceKey, Map<String, Object> ingestBody) {
    }
}
