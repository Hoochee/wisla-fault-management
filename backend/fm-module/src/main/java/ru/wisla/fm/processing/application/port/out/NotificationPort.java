package ru.wisla.fm.processing.application.port.out;

import java.util.UUID;

/** Delivery channel of a notify action. Still a no-op stub behind the adapter. */
public interface NotificationPort {

    void notify(UUID ruleId, String channel, String emailAddress);
}
