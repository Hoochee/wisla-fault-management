package ru.wisla.fm.health.application.service;

import ru.wisla.fm.health.application.port.in.GetProductHealthUseCase;
import ru.wisla.fm.health.application.port.out.HealthHistoryStorePort;
import ru.wisla.fm.health.application.port.out.HealthSnapshotStorePort;
import ru.wisla.fm.health.application.port.out.ProductTopologyPort;
import ru.wisla.fm.health.domain.ComponentHealth;
import ru.wisla.fm.health.domain.HealthHistoryBucket;
import ru.wisla.fm.health.domain.ProductHealthDetail;
import ru.wisla.fm.health.domain.ProductHealthSnapshot;
import ru.wisla.fm.health.domain.ProductHealthView;
import ru.wisla.fm.health.domain.ProductNotFoundException;
import ru.wisla.fm.health.domain.ProductTopology;
import ru.wisla.fm.health.domain.Sankey;
import ru.wisla.fm.health.domain.SnapshotPayload;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public final class GetProductHealthService implements GetProductHealthUseCase {

    private final ProductTopologyPort topologyPort;
    private final HealthSnapshotStorePort snapshotStore;
    private final HealthHistoryStorePort historyStore;

    public GetProductHealthService(
            ProductTopologyPort topologyPort,
            HealthSnapshotStorePort snapshotStore,
            HealthHistoryStorePort historyStore
    ) {
        this.topologyPort = topologyPort;
        this.snapshotStore = snapshotStore;
        this.historyStore = historyStore;
    }

    @Override
    public List<ProductHealthView> list(String tenant, String site, String tag) {
        return topologyPort.findAll().stream()
                .filter(product -> tenant == null || tenant.isBlank() || tenant.equals(product.tenant()))
                .filter(product -> site == null || site.isBlank() || site.equals(product.site()))
                .filter(product -> tag == null || tag.isBlank() || product.tags().contains(tag))
                .map(this::toView)
                .toList();
    }

    @Override
    public ProductHealthDetail get(UUID productId) {
        ProductTopology topology = topologyPort.findById(productId)
                .orElseThrow(ProductNotFoundException::new);
        ProductHealthView view = toView(topology);
        ProductHealthSnapshot snapshot = snapshotStore.findByProductId(productId).orElse(null);
        SnapshotPayload payload = snapshot != null
                ? snapshot.payload()
                : new SnapshotPayload(view.components(), List.of(), new Sankey(List.of(), List.of()));
        Instant calculatedAt = snapshot != null ? snapshot.calculatedAt() : null;
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endOfDay = startOfDay.plusSeconds(86400);
        List<HealthHistoryBucket> today = historyStore.findRange(productId, startOfDay, endOfDay);
        Integer minToday = today.stream().mapToInt(HealthHistoryBucket::minHealth).min().orElse(view.healthPercent());
        Integer maxToday = today.stream().mapToInt(HealthHistoryBucket::maxHealth).max().orElse(view.healthPercent());
        return new ProductHealthDetail(view, payload, calculatedAt, minToday, maxToday);
    }

    private ProductHealthView toView(ProductTopology topology) {
        ProductHealthSnapshot snapshot = snapshotStore.findByProductId(topology.productId()).orElse(null);
        int health = snapshot != null ? snapshot.healthPercent() : 100;
        int damage = snapshot != null ? snapshot.damagePercent() : 0;
        String maxSeverity = snapshot != null ? snapshot.maxSeverity() : "normal";
        int active = snapshot != null ? snapshot.activeEventCount() : 0;
        List<ComponentHealth> components = snapshot != null && snapshot.payload() != null
                ? snapshot.payload().components()
                : List.of();
        return new ProductHealthView(
                topology.productId(),
                topology.name(),
                topology.tenant(),
                topology.site(),
                maxSeverity,
                active,
                topology.ciIds(),
                topology.tags(),
                health,
                damage,
                components
        );
    }
}
