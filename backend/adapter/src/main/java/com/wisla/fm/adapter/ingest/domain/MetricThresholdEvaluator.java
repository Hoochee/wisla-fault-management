package com.wisla.fm.adapter.ingest.domain;

/**
 * Compares a scraped sample to warning/major/critical bands. Invert rules fire when the value is
 * at or below the threshold (e.g. {@code up == 0} is critical).
 */
public final class MetricThresholdEvaluator {

    public static final String OK = "ok";
    public static final String WARNING = "warning";
    public static final String MAJOR = "major";
    public static final String CRITICAL = "critical";

    public String evaluate(double value, ThresholdRule rule) {
        if (rule.invert()) {
            if (crossed(value, rule.critical(), true)) {
                return CRITICAL;
            }
            if (crossed(value, rule.major(), true)) {
                return MAJOR;
            }
            if (crossed(value, rule.warning(), true)) {
                return WARNING;
            }
            return OK;
        }
        if (crossed(value, rule.critical(), false)) {
            return CRITICAL;
        }
        if (crossed(value, rule.major(), false)) {
            return MAJOR;
        }
        if (crossed(value, rule.warning(), false)) {
            return WARNING;
        }
        return OK;
    }

    private static boolean crossed(double value, Double threshold, boolean invert) {
        if (threshold == null) {
            return false;
        }
        return invert ? value <= threshold : value >= threshold;
    }
}
