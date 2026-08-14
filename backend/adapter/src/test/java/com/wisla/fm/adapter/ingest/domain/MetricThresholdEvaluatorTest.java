package com.wisla.fm.adapter.ingest.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring-free domain tests for Prometheus pull threshold bands, including invert rules.
 */
class MetricThresholdEvaluatorTest {

    private final MetricThresholdEvaluator evaluator = new MetricThresholdEvaluator();

    @Test
    void valueBelowWarningIsOk() {
        ThresholdRule rule = cpuRule();

        assertThat(evaluator.evaluate(0.10, rule)).isEqualTo(MetricThresholdEvaluator.OK);
    }

    @Test
    void valueInWarningBandIsWarning() {
        ThresholdRule rule = cpuRule();

        assertThat(evaluator.evaluate(0.70, rule)).isEqualTo(MetricThresholdEvaluator.WARNING);
        assertThat(evaluator.evaluate(0.80, rule)).isEqualTo(MetricThresholdEvaluator.WARNING);
    }

    @Test
    void valueInMajorBandIsMajor() {
        ThresholdRule rule = cpuRule();

        assertThat(evaluator.evaluate(0.85, rule)).isEqualTo(MetricThresholdEvaluator.MAJOR);
        assertThat(evaluator.evaluate(0.90, rule)).isEqualTo(MetricThresholdEvaluator.MAJOR);
    }

    @Test
    void valueInCriticalBandIsCritical() {
        ThresholdRule rule = cpuRule();

        assertThat(evaluator.evaluate(0.95, rule)).isEqualTo(MetricThresholdEvaluator.CRITICAL);
        assertThat(evaluator.evaluate(1.20, rule)).isEqualTo(MetricThresholdEvaluator.CRITICAL);
    }

    @Test
    void invertUpZeroIsCritical() {
        ThresholdRule rule = new ThresholdRule("up", null, null, 0.0, true);

        assertThat(evaluator.evaluate(0.0, rule)).isEqualTo(MetricThresholdEvaluator.CRITICAL);
    }

    @Test
    void invertUpOneIsOk() {
        ThresholdRule rule = new ThresholdRule("up", null, null, 0.0, true);

        assertThat(evaluator.evaluate(1.0, rule)).isEqualTo(MetricThresholdEvaluator.OK);
    }

    private static ThresholdRule cpuRule() {
        return new ThresholdRule("process_cpu_usage", 0.70, 0.85, 0.95, false);
    }
}
