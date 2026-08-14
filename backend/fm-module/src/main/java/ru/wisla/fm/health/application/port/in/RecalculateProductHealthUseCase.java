package ru.wisla.fm.health.application.port.in;

import java.util.UUID;

public interface RecalculateProductHealthUseCase {

    void recalculate(UUID productId);

    void recalculateAll();

    void recalculateForCi(UUID ciId);
}
