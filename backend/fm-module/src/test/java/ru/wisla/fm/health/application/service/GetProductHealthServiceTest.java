package ru.wisla.fm.health.application.service;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.health.domain.CiMembership;
import ru.wisla.fm.health.domain.ComponentHealth;
import ru.wisla.fm.health.domain.ComponentNode;
import ru.wisla.fm.health.domain.HealthHistoryBucket;
import ru.wisla.fm.health.domain.InfluenceType;
import ru.wisla.fm.health.domain.ProductHealthDetail;
import ru.wisla.fm.health.domain.ProductHealthSnapshot;
import ru.wisla.fm.health.domain.ProductHealthView;
import ru.wisla.fm.health.domain.ProductNotFoundException;
import ru.wisla.fm.health.domain.ProductTopology;
import ru.wisla.fm.health.domain.Sankey;
import ru.wisla.fm.health.domain.SnapshotPayload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetProductHealthServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant T0 = Instant.parse("2026-08-13T12:00:00Z");
    private static final Instant T15 = Instant.parse("2026-08-13T12:15:00Z");
    private static final Instant T30 = Instant.parse("2026-08-13T12:30:00Z");

    private final InMemoryHealthPorts.Topology topology = new InMemoryHealthPorts.Topology();
    private final InMemoryHealthPorts.Snapshots snapshots = new InMemoryHealthPorts.Snapshots();
    private final InMemoryHealthPorts.History history = new InMemoryHealthPorts.History();

    @Test
    void listReturnsSnapshotPercents() {
        topology.with(demoProduct("moscow", "dc1"));
        snapshots.upsert(snapshot(75, 25, "warning"));

        List<ProductHealthView> rows = getService().list(null, null, null);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().healthPercent()).isEqualTo(75);
        assertThat(rows.getFirst().damagePercent()).isEqualTo(25);
        assertThat(rows.getFirst().maxSeverity()).isEqualTo("warning");
    }

    @Test
    void getReturnsSankeyFromSnapshot() {
        topology.with(demoProduct("moscow", "dc1"));
        snapshots.upsert(snapshot(75, 25, "warning"));

        ProductHealthDetail detail = getService().get(PRODUCT_ID);

        assertThat(detail.summary().healthPercent()).isEqualTo(75);
        assertThat(detail.payload().sankey().links()).isNotEmpty();
    }

    @Test
    void getUnknownProductThrows() {
        assertThatThrownBy(() -> getService().get(UUID.randomUUID()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void historyAggregates15MinuteBucketsOnRead() {
        topology.with(demoProduct("moscow", "dc1"));
        history.upsertBucket(new HealthHistoryBucket(UUID.randomUUID(), PRODUCT_ID, T0, 15, 70, 90, "minor"));
        history.upsertBucket(new HealthHistoryBucket(UUID.randomUUID(), PRODUCT_ID, T15, 15, 50, 80, "major"));
        history.upsertBucket(new HealthHistoryBucket(UUID.randomUUID(), PRODUCT_ID, T30, 15, 40, 60, "critical"));

        List<HealthHistoryBucket> hour = historyService().history(
                PRODUCT_ID,
                T0,
                Instant.parse("2026-08-13T13:00:00Z"),
                60
        );

        assertThat(hour).hasSize(1);
        assertThat(hour.getFirst().bucketMinutes()).isEqualTo(60);
        assertThat(hour.getFirst().minHealth()).isEqualTo(40);
        assertThat(hour.getFirst().maxHealth()).isEqualTo(90);
        assertThat(hour.getFirst().worstSeverity()).isEqualTo("critical");
    }

    private GetProductHealthService getService() {
        return new GetProductHealthService(topology, snapshots, history);
    }

    private GetProductHealthHistoryService historyService() {
        return new GetProductHealthHistoryService(topology, history);
    }

    private static ProductTopology demoProduct(String tenant, String site) {
        return new ProductTopology(
                PRODUCT_ID,
                "demo",
                tenant,
                site,
                List.of("core"),
                List.of(),
                List.of(new ComponentNode(
                        UUID.randomUUID(), "COMMON", "COMMON", 100,
                        InfluenceType.WEIGHTED, 100, 0, List.of()
                ))
        );
    }

    private static ProductHealthSnapshot snapshot(int health, int damage, String severity) {
        ComponentHealth component = new ComponentHealth(
                UUID.randomUUID(), "COMMON", "COMMON", 100, "weighted", 100, health, damage, List.of()
        );
        SnapshotPayload payload = new SnapshotPayload(
                List.of(component),
                List.of(),
                new Sankey(List.of(), List.of(new ru.wisla.fm.health.domain.SankeyLink("a", "b", damage)))
        );
        return new ProductHealthSnapshot(PRODUCT_ID, health, damage, severity, 2, payload, T0);
    }
}
