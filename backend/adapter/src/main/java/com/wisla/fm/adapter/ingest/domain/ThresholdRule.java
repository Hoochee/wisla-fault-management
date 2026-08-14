package com.wisla.fm.adapter.ingest.domain;

/**
 * Threshold band for a single Prometheus metric, parsed from {@code parserConfig.rules[]}.
 */
public record ThresholdRule(
        String metric,
        Double warning,
        Double major,
        Double critical,
        boolean invert
) {
}
