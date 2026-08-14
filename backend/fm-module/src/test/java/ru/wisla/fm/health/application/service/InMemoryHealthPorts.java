package ru.wisla.fm.health.application.service;

import ru.wisla.fm.health.application.port.out.ActiveSignalsPort;
import ru.wisla.fm.health.application.port.out.HealthHistoryStorePort;
import ru.wisla.fm.health.application.port.out.HealthSnapshotStorePort;
import ru.wisla.fm.health.application.port.out.ProductAggregateWritePort;
import ru.wisla.fm.health.application.port.out.ProductTopologyPort;
import ru.wisla.fm.health.domain.ActiveSignal;
import ru.wisla.fm.health.domain.ComponentDraft;
import ru.wisla.fm.health.domain.ComponentNode;
import ru.wisla.fm.health.domain.HealthHistoryBucket;
import ru.wisla.fm.health.domain.ProductHealthSnapshot;
import ru.wisla.fm.health.domain.ProductTopology;
import ru.wisla.fm.health.domain.SavedComponent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryHealthPorts {

    static final class Topology implements ProductTopologyPort {
        private final Map<UUID, ProductTopology> products = new HashMap<>();
        private final Map<UUID, List<SavedComponent>> components = new HashMap<>();
        int replaceCalls;
        int bindCalls;

        Topology with(ProductTopology topology) {
            products.put(topology.productId(), topology);
            components.put(topology.productId(), toSaved(topology));
            return this;
        }

        @Override
        public Optional<ProductTopology> findById(UUID productId) {
            return Optional.ofNullable(products.get(productId));
        }

        @Override
        public List<ProductTopology> findAll() {
            return List.copyOf(products.values());
        }

        @Override
        public List<UUID> findProductIdsByCiId(UUID ciId) {
            return products.values().stream()
                    .filter(p -> p.ciIds().contains(ciId)
                            || p.components().stream().anyMatch(c ->
                            c.cis().stream().anyMatch(m -> m.ciId().equals(ciId))))
                    .map(ProductTopology::productId)
                    .toList();
        }

        @Override
        public List<SavedComponent> replaceComponents(UUID productId, List<ComponentDraft> drafts) {
            replaceCalls++;
            List<SavedComponent> saved = drafts.stream()
                    .map(d -> new SavedComponent(
                            UUID.nameUUIDFromBytes(d.code().getBytes()),
                            d.code(),
                            d.name(),
                            d.weight(),
                            d.influenceType(),
                            d.criticalThreshold() == null ? 100 : d.criticalThreshold(),
                            d.sortOrder(),
                            d.ciIds() == null ? List.of() : d.ciIds()
                    ))
                    .toList();
            components.put(productId, saved);
            ProductTopology current = products.get(productId);
            if (current != null) {
                List<ComponentNode> nodes = saved.stream()
                        .map(s -> new ComponentNode(
                                s.id(), s.code(), s.name(), s.weight(),
                                ru.wisla.fm.health.domain.InfluenceType.fromWire(s.influenceType()),
                                s.criticalThreshold(), s.sortOrder(),
                                s.ciIds().stream()
                                        .map(id -> new ru.wisla.fm.health.domain.CiMembership(id, null))
                                        .toList()
                        ))
                        .toList();
                products.put(productId, new ProductTopology(
                        current.productId(), current.name(), current.tenant(), current.site(),
                        current.tags(), current.ciIds(), nodes
                ));
            }
            return saved;
        }

        @Override
        public List<SavedComponent> bindNewCisToCommon(UUID productId, List<UUID> ciIds) {
            bindCalls++;
            List<SavedComponent> existing = new ArrayList<>(listComponents(productId));
            SavedComponent common = existing.stream()
                    .filter(c -> "COMMON".equals(c.code()))
                    .findFirst()
                    .orElse(null);
            if (common == null) {
                common = new SavedComponent(
                        UUID.nameUUIDFromBytes("COMMON".getBytes()),
                        "COMMON",
                        "COMMON",
                        100,
                        "weighted",
                        100,
                        0,
                        new ArrayList<>()
                );
                existing.add(common);
            }
            java.util.Set<UUID> alreadySlotted = new java.util.HashSet<>();
            for (SavedComponent component : existing) {
                alreadySlotted.addAll(component.ciIds());
            }
            List<UUID> bound = new ArrayList<>(common.ciIds());
            for (UUID ciId : ciIds) {
                if (!alreadySlotted.contains(ciId)) {
                    bound.add(ciId);
                }
            }
            SavedComponent updatedCommon = new SavedComponent(
                    common.id(), common.code(), common.name(), common.weight(),
                    common.influenceType(), common.criticalThreshold(), common.sortOrder(), bound
            );
            List<SavedComponent> next = existing.stream()
                    .map(c -> "COMMON".equals(c.code()) ? updatedCommon : c)
                    .toList();
            components.put(productId, next);
            return next;
        }

        @Override
        public List<SavedComponent> listComponents(UUID productId) {
            return components.getOrDefault(productId, List.of());
        }

        private static List<SavedComponent> toSaved(ProductTopology topology) {
            return topology.components().stream()
                    .map(c -> new SavedComponent(
                            c.id(), c.code(), c.name(), c.weight(), c.influenceType().toWire(),
                            c.criticalThreshold(), c.sortOrder(),
                            c.cis().stream().map(m -> m.ciId()).toList()
                    ))
                    .toList();
        }
    }

    static final class Signals implements ActiveSignalsPort {
        private final List<ActiveSignal> signals = new ArrayList<>();

        Signals with(ActiveSignal signal) {
            signals.add(signal);
            return this;
        }

        @Override
        public List<ActiveSignal> findByCiIds(Collection<UUID> ciIds) {
            return signals.stream().filter(s -> ciIds.contains(s.ciId())).toList();
        }
    }

    static final class Snapshots implements HealthSnapshotStorePort {
        final Map<UUID, ProductHealthSnapshot> store = new ConcurrentHashMap<>();

        @Override
        public void upsert(ProductHealthSnapshot snapshot) {
            store.put(snapshot.productId(), snapshot);
        }

        @Override
        public Optional<ProductHealthSnapshot> findByProductId(UUID productId) {
            return Optional.ofNullable(store.get(productId));
        }

        @Override
        public List<ProductHealthSnapshot> findAll() {
            return List.copyOf(store.values());
        }
    }

    static final class History implements HealthHistoryStorePort {
        final Map<String, HealthHistoryBucket> store = new ConcurrentHashMap<>();

        @Override
        public void upsertBucket(HealthHistoryBucket bucket) {
            store.put(bucket.productId() + ":" + bucket.bucketStart(), bucket);
        }

        @Override
        public List<HealthHistoryBucket> findRange(UUID productId, Instant from, Instant to) {
            return store.values().stream()
                    .filter(b -> b.productId().equals(productId))
                    .filter(b -> !b.bucketStart().isBefore(from) && b.bucketStart().isBefore(to))
                    .toList();
        }
    }

    static final class ProductWrite implements ProductAggregateWritePort {
        final Map<UUID, String> maxSeverity = new ConcurrentHashMap<>();
        final Map<UUID, Integer> activeCount = new ConcurrentHashMap<>();

        @Override
        public void updateHealthFields(UUID productId, String severity, int activeEventCount) {
            maxSeverity.put(productId, severity);
            activeCount.put(productId, activeEventCount);
        }
    }
}
