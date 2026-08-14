package ru.wisla.fm.health.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record Sankey(List<SankeyNode> nodes, List<SankeyLink> links) {

    private static final Pattern UUID_TEXT = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    /**
     * Product → components (→ CI fqdn). Drops CI nodes labeled with raw UUIDs.
     * Also rewrites legacy CI → component → product snapshots on read.
     */
    public static Sankey forDisplay(Sankey source, String productName, Map<UUID, String> ciFqdn) {
        if (source == null || source.nodes() == null || source.links() == null) {
            return new Sankey(List.of(), List.of());
        }
        Map<UUID, String> fqdn = ciFqdn == null ? Map.of() : ciFqdn;
        List<SankeyNode> nodes = new ArrayList<>();
        for (SankeyNode node : source.nodes()) {
            if ("product".equals(node.kind())) {
                String label = blank(productName) ? readable(node.label(), node.id()) : productName;
                if (!looksLikeUuid(label)) {
                    nodes.add(new SankeyNode(node.id(), label, node.kind()));
                }
                continue;
            }
            if ("ci".equals(node.kind())) {
                UUID ciId = parsePrefixedUuid(node.id());
                String label = ciId != null ? fqdn.get(ciId) : node.label();
                if (blank(label) || looksLikeUuid(label)) {
                    continue;
                }
                nodes.add(new SankeyNode(node.id(), label, node.kind()));
                continue;
            }
            String label = readable(node.label(), node.id());
            if (looksLikeUuid(label)) {
                continue;
            }
            nodes.add(new SankeyNode(node.id(), label, node.kind()));
        }
        Set<String> keep = new LinkedHashSet<>();
        Set<String> productIds = new LinkedHashSet<>();
        Set<String> ciIds = new LinkedHashSet<>();
        for (SankeyNode node : nodes) {
            keep.add(node.id());
            if ("product".equals(node.kind())) {
                productIds.add(node.id());
            } else if ("ci".equals(node.kind())) {
                ciIds.add(node.id());
            }
        }
        List<SankeyLink> links = new ArrayList<>();
        for (SankeyLink link : source.links()) {
            String from = link.from();
            String to = link.to();
            if (productIds.contains(to) && !productIds.contains(from)) {
                String swap = from;
                from = to;
                to = swap;
            } else if (ciIds.contains(from) && !ciIds.contains(to)) {
                String swap = from;
                from = to;
                to = swap;
            }
            if (!keep.contains(from) || !keep.contains(to) || from.equals(to)) {
                continue;
            }
            links.add(new SankeyLink(from, to, link.damage()));
        }
        return new Sankey(List.copyOf(nodes), List.copyOf(links));
    }

    static boolean looksLikeUuid(String value) {
        return value != null && UUID_TEXT.matcher(value).matches();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String readable(String label, String id) {
        if (!blank(label) && !looksLikeUuid(label)) {
            return label;
        }
        if (id != null && id.contains(":")) {
            String suffix = id.substring(id.indexOf(':') + 1);
            if (!blank(suffix) && !looksLikeUuid(suffix)) {
                return suffix;
            }
        }
        return label;
    }

    private static UUID parsePrefixedUuid(String id) {
        if (id == null) {
            return null;
        }
        String raw = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (!looksLikeUuid(raw)) {
            return null;
        }
        return UUID.fromString(raw);
    }
}
