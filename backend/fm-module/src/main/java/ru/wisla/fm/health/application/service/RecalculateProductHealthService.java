package ru.wisla.fm.health.application.service;

import ru.wisla.fm.health.application.port.in.RecalculateProductHealthUseCase;
import ru.wisla.fm.health.application.port.out.ActiveSignalsPort;
import ru.wisla.fm.health.application.port.out.HealthHistoryStorePort;
import ru.wisla.fm.health.application.port.out.HealthSnapshotStorePort;
import ru.wisla.fm.health.application.port.out.ProductAggregateWritePort;
import ru.wisla.fm.health.application.port.out.ProductTopologyPort;
import ru.wisla.fm.health.domain.ActiveSignal;
import ru.wisla.fm.health.domain.CiMembership;
import ru.wisla.fm.health.domain.ComponentNode;
import ru.wisla.fm.health.domain.HealthCalculation;
import ru.wisla.fm.health.domain.HealthCalculator;
import ru.wisla.fm.health.domain.HealthHistoryBucket;
import ru.wisla.fm.health.domain.ProductHealthSnapshot;
import ru.wisla.fm.health.domain.ProductTopology;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RecalculateProductHealthService implements RecalculateProductHealthUseCase {

    static final int PERSIST_BUCKET_MINUTES = 15;

    private final ProductTopologyPort topologyPort;
    private final ActiveSignalsPort activeSignalsPort;
    private final HealthSnapshotStorePort snapshotStore;
    private final HealthHistoryStorePort historyStore;
    private final ProductAggregateWritePort productWrite;
    private final HealthCalculator calculator;
    private final Clock clock;

    public RecalculateProductHealthService(
            ProductTopologyPort topologyPort,
            ActiveSignalsPort activeSignalsPort,
            HealthSnapshotStorePort snapshotStore,
            HealthHistoryStorePort historyStore,
            ProductAggregateWritePort productWrite,
            HealthCalculator calculator,
            Clock clock
    ) {
        this.topologyPort = topologyPort;
        this.activeSignalsPort = activeSignalsPort;
        this.snapshotStore = snapshotStore;
        this.historyStore = historyStore;
        this.productWrite = productWrite;
        this.calculator = calculator;
        this.clock = clock;
    }

    @Override
    public void recalculate(UUID productId) {
        topologyPort.findById(productId).ifPresent(this::recalculateTopology);
    }

    @Override
    public void recalculateAll() {
        for (ProductTopology topology : topologyPort.findAll()) {
            if (!topology.components().isEmpty()) {
                recalculateTopology(topology);
            }
        }
    }

    @Override
    public void recalculateForCi(UUID ciId) {
        if (ciId == null) {
            return;
        }
        for (UUID productId : topologyPort.findProductIdsByCiId(ciId)) {
            recalculate(productId);
        }
    }

    private void recalculateTopology(ProductTopology topology) {
        List<UUID> ciIds = List.copyOf(collectCiIds(topology));
        List<ActiveSignal> signals = ciIds.isEmpty() ? List.of() : activeSignalsPort.findByCiIds(ciIds);
        Map<UUID, String> worstByCi = new HashMap<>();
        for (ActiveSignal signal : signals) {
            worstByCi.merge(signal.ciId(), signal.severity(), HealthCalculator::worseSeverity);
        }
        Map<UUID, Integer> ciHealth = new HashMap<>();
        for (UUID ciId : ciIds) {
            ciHealth.put(ciId, HealthCalculator.ciHealthFromWorstSeverity(worstByCi.get(ciId)));
        }
        HealthCalculation calculation = calculator.calculate(topology, ciHealth);
        String maxSeverity = "normal";
        for (String severity : worstByCi.values()) {
            maxSeverity = HealthCalculator.worseSeverity(maxSeverity, severity);
        }
        Instant now = clock.instant();
        ProductHealthSnapshot snapshot = new ProductHealthSnapshot(
                topology.productId(),
                calculation.healthPercent(),
                calculation.damagePercent(),
                maxSeverity,
                signals.size(),
                calculation.payload(),
                now
        );
        snapshotStore.upsert(snapshot);
        upsertHistory(snapshot);
        productWrite.updateHealthFields(topology.productId(), maxSeverity, signals.size());
    }

    private static Set<UUID> collectCiIds(ProductTopology topology) {
        Set<UUID> ciIds = new LinkedHashSet<>(topology.ciIds());
        for (ComponentNode component : topology.components()) {
            for (CiMembership membership : component.cis()) {
                ciIds.add(membership.ciId());
            }
        }
        return ciIds;
    }

    private void upsertHistory(ProductHealthSnapshot snapshot) {
        Instant bucketStart = HealthHistoryBucket.floorToBucket(snapshot.calculatedAt(), PERSIST_BUCKET_MINUTES);
        Instant bucketEnd = bucketStart.plus(Duration.ofMinutes(PERSIST_BUCKET_MINUTES));
        List<HealthHistoryBucket> existing = historyStore.findRange(snapshot.productId(), bucketStart, bucketEnd);
        int minHealth = snapshot.healthPercent();
        int maxHealth = snapshot.healthPercent();
        String worst = snapshot.maxSeverity();
        UUID id = UUID.randomUUID();
        if (!existing.isEmpty()) {
            HealthHistoryBucket current = existing.getFirst();
            id = current.id() != null ? current.id() : id;
            minHealth = Math.min(current.minHealth(), minHealth);
            maxHealth = Math.max(current.maxHealth(), maxHealth);
            worst = HealthCalculator.worseSeverity(current.worstSeverity(), worst);
        }
        historyStore.upsertBucket(new HealthHistoryBucket(
                id,
                snapshot.productId(),
                bucketStart,
                PERSIST_BUCKET_MINUTES,
                minHealth,
                maxHealth,
                worst
        ));
    }
}
