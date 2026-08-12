package com.wisla.fm.adapter.ingest.domain;

import java.util.UUID;

/**
 * Result of an accepted ingest attempt: either published downstream or parked in the buffer.
 */
public record DeliveryOutcome(String delivery, UUID messageId) {

    public static final String FORWARDED = "forwarded";
    public static final String BUFFERED = "buffered";

    public static DeliveryOutcome forwarded() {
        return new DeliveryOutcome(FORWARDED, null);
    }

    public static DeliveryOutcome buffered(UUID messageId) {
        return new DeliveryOutcome(BUFFERED, messageId);
    }

    public boolean isForwarded() {
        return FORWARDED.equals(delivery);
    }
}
