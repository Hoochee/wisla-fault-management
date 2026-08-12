package com.wisla.fm.adapter.ingest.application.port.out;

import java.util.Map;
import java.util.UUID;

public interface RawEventPublisherPort {

    PublishResult publish(UUID sourceId, String sourceKey, Map<String, Object> ingestBody);

    record PublishResult(
            boolean success,
            String error,
            boolean retryable
    ) {
        public static PublishResult ok() {
            return new PublishResult(true, null, false);
        }

        public static PublishResult retryable(String error) {
            return new PublishResult(false, error, true);
        }

        public static PublishResult permanent(String error) {
            return new PublishResult(false, error, false);
        }
    }
}
