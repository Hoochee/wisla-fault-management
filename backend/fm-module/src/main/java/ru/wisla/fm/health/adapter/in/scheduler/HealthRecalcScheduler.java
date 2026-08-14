package ru.wisla.fm.health.adapter.in.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.wisla.fm.health.application.port.in.RecalculateProductHealthUseCase;

@Component
public class HealthRecalcScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthRecalcScheduler.class);

    private final RecalculateProductHealthUseCase recalculateProductHealth;

    public HealthRecalcScheduler(RecalculateProductHealthUseCase recalculateProductHealth) {
        this.recalculateProductHealth = recalculateProductHealth;
    }

    @Scheduled(fixedRate = 300_000, initialDelay = 300_000)
    public void recalculateAll() {
        try {
            recalculateProductHealth.recalculateAll();
        } catch (Exception ex) {
            log.warn("Scheduled product health recalculation failed: {}", ex.getMessage());
        }
    }
}
