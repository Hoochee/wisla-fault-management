package ru.wisla.fm.health.application.service;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.health.domain.ActiveSignal;
import ru.wisla.fm.health.domain.CiMembership;
import ru.wisla.fm.health.domain.ComponentNode;
import ru.wisla.fm.health.domain.HealthHistoryBucket;
import ru.wisla.fm.health.domain.InfluenceType;
import ru.wisla.fm.health.domain.ProductHealthSnapshot;
import ru.wisla.fm.health.domain.ProductTopology;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecalculateProductHealthServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-13T12:07:00Z");
    private static final UUID PRODUCT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CI_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final InMemoryHealthPorts.Topology topology = new InMemoryHealthPorts.Topology();
    private final InMemoryHealthPorts.Signals signals = new InMemoryHealthPorts.Signals();
    private final InMemoryHealthPorts.Snapshots snapshots = new InMemoryHealthPorts.Snapshots();
    private final InMemoryHealthPorts.History history = new InMemoryHealthPorts.History();
    private final InMemoryHealthPorts.ProductWrite products = new InMemoryHealthPorts.ProductWrite();

    @Test
    void snapshotUpsertContainsPercentsSeverityAndPayload() {
        topology.with(demoProduct());
        signals.with(new ActiveSignal(UUID.randomUUID(), CI_ID, "major", "disk"));

        service().recalculate(PRODUCT_ID);

        ProductHealthSnapshot snapshot = snapshots.store.get(PRODUCT_ID);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.healthPercent()).isEqualTo(50);
        assertThat(snapshot.damagePercent()).isEqualTo(50);
        assertThat(snapshot.maxSeverity()).isEqualTo("major");
        assertThat(snapshot.activeEventCount()).isEqualTo(1);
        assertThat(snapshot.payload()).isNotNull();
        assertThat(snapshot.payload().sankey().links()).isNotEmpty();
        assertThat(snapshot.calculatedAt()).isEqualTo(NOW);
        assertThat(products.maxSeverity.get(PRODUCT_ID)).isEqualTo("major");
        assertThat(products.activeCount.get(PRODUCT_ID)).isEqualTo(1);
    }

    @Test
    void historyBucketIsUpsertedOn15MinuteFloor() {
        topology.with(demoProduct());
        signals.with(new ActiveSignal(UUID.randomUUID(), CI_ID, "warning", "cpu"));

        service().recalculate(PRODUCT_ID);

        Instant bucketStart = HealthHistoryBucket.floorToBucket(NOW, 15);
        HealthHistoryBucket bucket = history.store.get(PRODUCT_ID + ":" + bucketStart);
        assertThat(bucket).isNotNull();
        assertThat(bucket.bucketMinutes()).isEqualTo(15);
        assertThat(bucket.bucketStart()).isEqualTo(Instant.parse("2026-08-13T12:00:00Z"));
        assertThat(bucket.minHealth()).isEqualTo(75);
        assertThat(bucket.maxHealth()).isEqualTo(75);
        assertThat(bucket.worstSeverity()).isEqualTo("warning");
    }

    @Test
    void historyBucketMergesMinMaxAndWorstSeverity() {
        topology.with(demoProduct());
        Instant bucketStart = Instant.parse("2026-08-13T12:00:00Z");
        history.upsertBucket(new HealthHistoryBucket(
                UUID.randomUUID(), PRODUCT_ID, bucketStart, 15, 90, 100, "warning"
        ));
        signals.with(new ActiveSignal(UUID.randomUUID(), CI_ID, "critical", "down"));

        service().recalculate(PRODUCT_ID);

        HealthHistoryBucket bucket = history.store.get(PRODUCT_ID + ":" + bucketStart);
        assertThat(bucket.minHealth()).isEqualTo(25);
        assertThat(bucket.maxHealth()).isEqualTo(100);
        assertThat(bucket.worstSeverity()).isEqualTo("critical");
    }

    private RecalculateProductHealthService service() {
        return new RecalculateProductHealthService(
                topology,
                signals,
                snapshots,
                history,
                products,
                new ru.wisla.fm.health.domain.HealthCalculator(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static ProductTopology demoProduct() {
        return new ProductTopology(
                PRODUCT_ID,
                "demo",
                "moscow",
                "dc1",
                List.of(),
                List.of(CI_ID),
                List.of(new ComponentNode(
                        UUID.nameUUIDFromBytes("COMMON".getBytes()),
                        "COMMON",
                        "COMMON",
                        100,
                        InfluenceType.WEIGHTED,
                        100,
                        0,
                        List.of(new CiMembership(CI_ID, 100))
                ))
        );
    }
}
