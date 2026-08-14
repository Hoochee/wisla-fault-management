package ru.wisla.fm.health.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure Monq health formula: {@code H = min(h_direct, h_ratio)}. No Spring, no I/O.
 */
public final class HealthCalculator {

    private static final List<String> SEVERITY_ORDER = List.of(
            "fatal", "critical", "major", "minor", "warning", "normal"
    );

    public static int ciHealthFromWorstSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return 100;
        }
        return switch (severity.toLowerCase()) {
            case "fatal" -> 0;
            case "critical" -> 25;
            case "major" -> 50;
            case "minor" -> 62;
            case "warning" -> 75;
            default -> 100;
        };
    }

    public static String worseSeverity(String left, String right) {
        return SEVERITY_ORDER.indexOf(normalizeSeverity(left))
                <= SEVERITY_ORDER.indexOf(normalizeSeverity(right))
                ? normalizeSeverity(left)
                : normalizeSeverity(right);
    }

    public HealthCalculation calculate(ProductTopology topology, Map<UUID, Integer> ciHealthMap) {
        Map<UUID, Integer> health = ciHealthMap == null ? Map.of() : ciHealthMap;
        List<SankeyNode> nodes = new ArrayList<>();
        List<SankeyLink> links = new ArrayList<>();
        List<ComponentHealth> componentHealths = new ArrayList<>();
        List<SignalContribution> signals = new ArrayList<>();

        String productNodeId = "product:" + topology.productId();
        nodes.add(new SankeyNode(productNodeId, topology.name(), "product"));

        List<Child> componentChildren = new ArrayList<>();
        for (ComponentNode component : topology.components()) {
            List<Child> ciChildren = new ArrayList<>();
            List<UUID> ciIds = new ArrayList<>();
            String componentNodeId = "component:" + component.code();
            String componentLabel = component.name() != null && !component.name().isBlank()
                    ? component.name()
                    : component.code();
            nodes.add(new SankeyNode(componentNodeId, componentLabel, "component"));

            for (CiMembership membership : component.cis()) {
                UUID ciId = membership.ciId();
                ciIds.add(ciId);
                int h = health.getOrDefault(ciId, 100);
                int weight = membership.weight() != null ? membership.weight() : component.weight();
                String ciNodeId = "ci:" + ciId;
                ciChildren.add(new Child(
                        ciNodeId,
                        ciId.toString(),
                        h,
                        weight,
                        component.influenceType(),
                        component.criticalThreshold()
                ));
                nodes.add(new SankeyNode(ciNodeId, ciId.toString(), "ci"));
                if (h < 100) {
                    signals.add(new SignalContribution(ciId, severityFromHealth(h), h));
                }
            }

            Aggregate ciAggregate = aggregate(ciChildren);
            for (Child ci : ciChildren) {
                links.add(new SankeyLink(
                        componentNodeId,
                        ci.nodeId(),
                        ciAggregate.damageById().getOrDefault(ci.nodeId(), 0)
                ));
            }

            componentHealths.add(new ComponentHealth(
                    component.id(),
                    component.code(),
                    component.name(),
                    component.weight(),
                    component.influenceType().toWire(),
                    component.criticalThreshold(),
                    ciAggregate.health(),
                    100 - ciAggregate.health(),
                    List.copyOf(ciIds)
            ));
            componentChildren.add(new Child(
                    componentNodeId,
                    component.code(),
                    ciAggregate.health(),
                    component.weight(),
                    component.influenceType(),
                    component.criticalThreshold()
            ));
        }

        Aggregate productAggregate = aggregate(componentChildren);
        for (Child componentChild : componentChildren) {
            links.add(new SankeyLink(
                    productNodeId,
                    componentChild.nodeId(),
                    productAggregate.damageById().getOrDefault(componentChild.nodeId(), 0)
            ));
        }

        List<ComponentHealth> withProductDamage = new ArrayList<>();
        for (ComponentHealth componentHealth : componentHealths) {
            String nodeId = "component:" + componentHealth.code();
            int damage = productAggregate.damageById().getOrDefault(nodeId, 0);
            withProductDamage.add(new ComponentHealth(
                    componentHealth.id(),
                    componentHealth.code(),
                    componentHealth.name(),
                    componentHealth.weight(),
                    componentHealth.influenceType(),
                    componentHealth.criticalThreshold(),
                    componentHealth.healthPercent(),
                    damage,
                    componentHealth.ciIds()
            ));
        }

        SnapshotPayload payload = new SnapshotPayload(
                List.copyOf(withProductDamage),
                List.copyOf(signals),
                new Sankey(List.copyOf(nodes), List.copyOf(links))
        );
        return new HealthCalculation(productAggregate.health(), 100 - productAggregate.health(), payload);
    }

    private static Aggregate aggregate(List<Child> children) {
        if (children.isEmpty()) {
            return new Aggregate(100, Map.of());
        }

        Integer hDirect = null;
        for (Child child : children) {
            if (child.influenceType() == InfluenceType.CRITICAL && child.health() < child.criticalThreshold()) {
                if (hDirect == null || child.health() < hDirect) {
                    hDirect = child.health();
                }
            }
        }

        int totalWeight = children.stream().mapToInt(Child::weight).sum();
        int hRatio;
        if (totalWeight <= 0) {
            hRatio = 100;
        } else {
            double sum = 0;
            for (Child child : children) {
                sum += (child.weight() / (double) totalWeight) * child.health();
            }
            hRatio = (int) Math.round(sum);
        }

        int health;
        if (hDirect == null) {
            health = totalWeight <= 0 ? 100 : hRatio;
        } else if (totalWeight <= 0) {
            health = hDirect;
        } else {
            health = Math.min(hDirect, hRatio);
        }

        Map<String, Integer> damageById = new LinkedHashMap<>();
        List<Child> minima = new ArrayList<>();
        if (hDirect != null) {
            for (Child child : children) {
                if (child.health() == hDirect && child.influenceType() == InfluenceType.CRITICAL) {
                    minima.add(child);
                }
            }
        }

        if (minima.size() > 1) {
            int totalDamage = 100 - hDirect;
            int base = totalDamage / minima.size();
            int remainder = totalDamage % minima.size();
            for (int i = 0; i < minima.size(); i++) {
                damageById.put(minima.get(i).nodeId(), base + (i < remainder ? 1 : 0));
            }
            for (Child child : children) {
                damageById.putIfAbsent(child.nodeId(), perLinkDamage(child, totalWeight));
            }
        } else {
            for (Child child : children) {
                damageById.put(child.nodeId(), perLinkDamage(child, totalWeight));
            }
        }
        return new Aggregate(health, damageById);
    }

    private static int perLinkDamage(Child child, int totalWeight) {
        int deficit = 100 - child.health();
        if (child.influenceType() == InfluenceType.CRITICAL) {
            return deficit;
        }
        if (totalWeight <= 0 || child.weight() <= 0) {
            return 0;
        }
        return (int) Math.round(deficit * (child.weight() / (double) totalWeight));
    }

    private static String severityFromHealth(int health) {
        if (health <= 0) {
            return "fatal";
        }
        if (health <= 25) {
            return "critical";
        }
        if (health <= 50) {
            return "major";
        }
        if (health <= 62) {
            return "minor";
        }
        if (health <= 75) {
            return "warning";
        }
        return "normal";
    }

    private static String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "normal";
        }
        String lower = severity.toLowerCase();
        return SEVERITY_ORDER.contains(lower) ? lower : "normal";
    }

    private record Child(
            String nodeId,
            String label,
            int health,
            int weight,
            InfluenceType influenceType,
            int criticalThreshold
    ) {
    }

    private record Aggregate(int health, Map<String, Integer> damageById) {
    }
}
