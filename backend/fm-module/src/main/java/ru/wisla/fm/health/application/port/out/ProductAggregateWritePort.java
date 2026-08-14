package ru.wisla.fm.health.application.port.out;

import java.util.UUID;

public interface ProductAggregateWritePort {

    void updateHealthFields(UUID productId, String maxSeverity, int activeEventCount);
}
