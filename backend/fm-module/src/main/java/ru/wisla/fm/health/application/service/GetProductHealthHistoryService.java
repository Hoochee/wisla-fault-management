package ru.wisla.fm.health.application.service;

import ru.wisla.fm.health.application.port.in.GetProductHealthHistoryUseCase;
import ru.wisla.fm.health.application.port.out.HealthHistoryStorePort;
import ru.wisla.fm.health.application.port.out.ProductTopologyPort;
import ru.wisla.fm.health.domain.HealthCalculator;
import ru.wisla.fm.health.domain.HealthHistoryBucket;
import ru.wisla.fm.health.domain.ProductNotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GetProductHealthHistoryService implements GetProductHealthHistoryUseCase {

    private static final int DEFAULT_BUCKET_MINUTES = 15;

    private final ProductTopologyPort topologyPort;
    private final HealthHistoryStorePort historyStore;

    public GetProductHealthHistoryService(ProductTopologyPort topologyPort, HealthHistoryStorePort historyStore) {
        this.topologyPort = topologyPort;
        this.historyStore = historyStore;
    }

    @Override
    public List<HealthHistoryBucket> history(UUID productId, Instant from, Instant to, int bucketMinutes) {
        topologyPort.findById(productId).orElseThrow(ProductNotFoundException::new);
        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now();
        int requested = bucketMinutes > 0 ? bucketMinutes : DEFAULT_BUCKET_MINUTES;
        List<HealthHistoryBucket> stored = historyStore.findRange(productId, start, end).stream()
                .sorted(Comparator.comparing(HealthHistoryBucket::bucketStart))
                .toList();
        if (requested <= DEFAULT_BUCKET_MINUTES) {
            return stored;
        }
        return aggregate(stored, productId, requested);
    }

    private static List<HealthHistoryBucket> aggregate(
            List<HealthHistoryBucket> stored,
            UUID productId,
            int bucketMinutes
    ) {
        Map<Instant, List<HealthHistoryBucket>> groups = new LinkedHashMap<>();
        for (HealthHistoryBucket bucket : stored) {
            Instant key = HealthHistoryBucket.floorToBucket(bucket.bucketStart(), bucketMinutes);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(bucket);
        }
        List<HealthHistoryBucket> result = new ArrayList<>();
        for (Map.Entry<Instant, List<HealthHistoryBucket>> entry : groups.entrySet()) {
            int min = entry.getValue().stream().mapToInt(HealthHistoryBucket::minHealth).min().orElse(100);
            int max = entry.getValue().stream().mapToInt(HealthHistoryBucket::maxHealth).max().orElse(100);
            String worst = "normal";
            for (HealthHistoryBucket bucket : entry.getValue()) {
                worst = HealthCalculator.worseSeverity(worst, bucket.worstSeverity());
            }
            result.add(new HealthHistoryBucket(
                    UUID.randomUUID(),
                    productId,
                    entry.getKey(),
                    bucketMinutes,
                    min,
                    max,
                    worst
            ));
        }
        result.sort(Comparator.comparing(HealthHistoryBucket::bucketStart));
        return result;
    }
}
