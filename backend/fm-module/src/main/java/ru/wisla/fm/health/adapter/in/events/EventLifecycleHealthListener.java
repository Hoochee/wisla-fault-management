package ru.wisla.fm.health.adapter.in.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.wisla.fm.health.application.port.in.RecalculateProductHealthUseCase;
import ru.wisla.fm.processing.domain.EventClosed;
import ru.wisla.fm.processing.domain.EventCreated;
import ru.wisla.fm.processing.domain.EventUpdated;

@Component
public class EventLifecycleHealthListener {

    private static final Logger log = LoggerFactory.getLogger(EventLifecycleHealthListener.class);

    private final RecalculateProductHealthUseCase recalculateProductHealth;

    public EventLifecycleHealthListener(RecalculateProductHealthUseCase recalculateProductHealth) {
        this.recalculateProductHealth = recalculateProductHealth;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCreated(EventCreated event) {
        recalc(event.ciId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUpdated(EventUpdated event) {
        recalc(event.ciId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClosed(EventClosed event) {
        recalc(event.ciId());
    }

    private void recalc(java.util.UUID ciId) {
        try {
            recalculateProductHealth.recalculateForCi(ciId);
        } catch (Exception ex) {
            log.warn("Product health recalculation failed for CI {}: {}", ciId, ex.getMessage());
        }
    }
}
