package com.wisla.fm.adapter.ingest.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A Prometheus scrape target from {@code parserConfig.targets}.
 */
public record PullTarget(String url, String ciFqdn) {

    public static List<PullTarget> fromParserConfig(Map<String, Object> parserConfig) {
        if (parserConfig == null) {
            return List.of();
        }
        Object raw = parserConfig.get("targets");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<PullTarget> targets = new ArrayList<>();
        for (Object item : list) {
            PullTarget target = fromItem(item);
            if (target != null) {
                targets.add(target);
            }
        }
        return List.copyOf(targets);
    }

    @SuppressWarnings("unchecked")
    private static PullTarget fromItem(Object item) {
        if (item instanceof String url && !url.isBlank()) {
            return new PullTarget(url, hostOf(url));
        }
        if (item instanceof Map<?, ?> map) {
            Map<String, Object> typed = (Map<String, Object>) map;
            Object urlValue = typed.get("url");
            if (urlValue == null) {
                return null;
            }
            String url = String.valueOf(urlValue);
            Object fqdnValue = typed.get("ciFqdn");
            String ciFqdn = fqdnValue != null && !String.valueOf(fqdnValue).isBlank()
                    ? String.valueOf(fqdnValue)
                    : hostOf(url);
            return new PullTarget(url, ciFqdn);
        }
        return null;
    }

    private static String hostOf(String url) {
        int scheme = url.indexOf("://");
        int start = scheme >= 0 ? scheme + 3 : 0;
        int end = url.indexOf('/', start);
        String hostPort = end >= 0 ? url.substring(start, end) : url.substring(start);
        int colon = hostPort.indexOf(':');
        return colon >= 0 ? hostPort.substring(0, colon) : hostPort;
    }
}
