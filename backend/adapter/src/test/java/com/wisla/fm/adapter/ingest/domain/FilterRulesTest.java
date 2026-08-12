package com.wisla.fm.adapter.ingest.domain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the pre-filter decision that used to live in {@code FilterService.shouldDrop}.
 */
class FilterRulesTest {

    // --- rule-set level gates ------------------------------------------------------------------

    @Test
    void nullRulesNeverDrop() {
        assertThat(FilterRules.of(null).shouldDrop(payload("severity", "low"))).isFalse();
    }

    @Test
    void emptyRulesNeverDrop() {
        assertThat(FilterRules.of(Map.of()).shouldDrop(payload("severity", "low"))).isFalse();
    }

    @Test
    void enabledFalseDisablesEveryCondition() {
        FilterRules rules = FilterRules.of(Map.of(
                "enabled", false,
                "drop_if", List.of(condition("severity", "eq", "low"))
        ));

        assertThat(rules.shouldDrop(payload("severity", "low"))).isFalse();
    }

    /** Only the Boolean {@code false} disables; any other value leaves the rules active. */
    @Test
    void nonBooleanEnabledValueLeavesRulesActive() {
        FilterRules rules = FilterRules.of(Map.of(
                "enabled", "false",
                "drop_if", List.of(condition("severity", "eq", "low"))
        ));

        assertThat(rules.shouldDrop(payload("severity", "low"))).isTrue();
    }

    // --- drop_if -------------------------------------------------------------------------------

    @Test
    void dropIfDropsOnTheFirstMatchingCondition() {
        FilterRules rules = FilterRules.of(Map.of("drop_if", List.of(
                condition("severity", "eq", "high"),
                condition("severity", "eq", "low")
        )));

        assertThat(rules.shouldDrop(payload("severity", "low"))).isTrue();
    }

    @Test
    void dropIfKeepsThePayloadWhenNoConditionMatches() {
        FilterRules rules = FilterRules.of(Map.of("drop_if", List.of(
                condition("severity", "eq", "high")
        )));

        assertThat(rules.shouldDrop(payload("severity", "low"))).isFalse();
    }

    @Test
    void nonListDropIfIsIgnored() {
        FilterRules rules = FilterRules.of(Map.of("drop_if", "not-a-list"));

        assertThat(rules.shouldDrop(payload("severity", "low"))).isFalse();
    }

    // --- pass_only -----------------------------------------------------------------------------

    @Test
    void passOnlyKeepsThePayloadWhenAnyConditionMatches() {
        FilterRules rules = FilterRules.of(Map.of("pass_only", List.of(
                condition("severity", "eq", "high"),
                condition("severity", "eq", "low")
        )));

        assertThat(rules.shouldDrop(payload("severity", "low"))).isFalse();
    }

    @Test
    void passOnlyDropsThePayloadWhenNoConditionMatches() {
        FilterRules rules = FilterRules.of(Map.of("pass_only", List.of(
                condition("severity", "eq", "high")
        )));

        assertThat(rules.shouldDrop(payload("severity", "low"))).isTrue();
    }

    @Test
    void emptyPassOnlyListIsIgnored() {
        FilterRules rules = FilterRules.of(Map.of("pass_only", List.of()));

        assertThat(rules.shouldDrop(payload("severity", "low"))).isFalse();
    }

    /** drop_if is evaluated first, so a payload matching both is dropped. */
    @Test
    void dropIfWinsOverPassOnly() {
        FilterRules rules = FilterRules.of(Map.of(
                "drop_if", List.of(condition("severity", "eq", "low")),
                "pass_only", List.of(condition("severity", "eq", "low"))
        ));

        assertThat(rules.shouldDrop(payload("severity", "low"))).isTrue();
    }

    // --- operators -----------------------------------------------------------------------------

    @Test
    void eqComparesStringRepresentations() {
        assertThat(dropsOn(condition("code", "eq", "5"), payload("code", 5))).isTrue();
        assertThat(dropsOn(condition("code", "eq", "6"), payload("code", 5))).isFalse();
    }

    @Test
    void neIsTheNegationOfEq() {
        assertThat(dropsOn(condition("code", "ne", "6"), payload("code", 5))).isTrue();
        assertThat(dropsOn(condition("code", "ne", "5"), payload("code", 5))).isFalse();
    }

    /** ne on a missing field compares "null" with the expected value, so it matches. */
    @Test
    void neMatchesWhenTheFieldIsMissing() {
        assertThat(dropsOn(condition("absent", "ne", "5"), payload("code", 5))).isTrue();
    }

    @Test
    void containsChecksSubstringAndRequiresAPresentValue() {
        assertThat(dropsOn(condition("title", "contains", "disk"), payload("title", "disk full"))).isTrue();
        assertThat(dropsOn(condition("title", "contains", "cpu"), payload("title", "disk full"))).isFalse();
        assertThat(dropsOn(condition("absent", "contains", "disk"), payload("title", "disk full"))).isFalse();
    }

    @Test
    void inMatchesAnyListMember() {
        assertThat(dropsOn(condition("severity", "in", List.of("low", "info")), payload("severity", "low"))).isTrue();
        assertThat(dropsOn(condition("severity", "in", List.of("low", "info")), payload("severity", "high"))).isFalse();
    }

    @Test
    void inWithANonListExpectedValueNeverMatches() {
        assertThat(dropsOn(condition("severity", "in", "low"), payload("severity", "low"))).isFalse();
    }

    @Test
    void gtAndLtCompareNumerically() {
        assertThat(dropsOn(condition("value", "gt", 5), payload("value", 10))).isTrue();
        assertThat(dropsOn(condition("value", "gt", 5), payload("value", 1))).isFalse();
        assertThat(dropsOn(condition("value", "lt", 5), payload("value", 1))).isTrue();
        assertThat(dropsOn(condition("value", "lt", 5), payload("value", 10))).isFalse();
    }

    /** Unparseable numbers compare as equal, so neither gt nor lt matches. */
    @Test
    void nonNumericComparandsCompareAsEqual() {
        assertThat(dropsOn(condition("value", "gt", 5), payload("value", "abc"))).isFalse();
        assertThat(dropsOn(condition("value", "lt", 5), payload("value", "abc"))).isFalse();
    }

    @Test
    void existsMatchesOnlyWhenTheFieldIsPresent() {
        assertThat(dropsOn(condition("value", "exists", null), payload("value", "anything"))).isTrue();
        assertThat(dropsOn(condition("absent", "exists", null), payload("value", "anything"))).isFalse();
    }

    @Test
    void unknownOperatorNeverMatches() {
        assertThat(dropsOn(condition("severity", "regex", "lo.*"), payload("severity", "low"))).isFalse();
    }

    @Test
    void conditionWithoutFieldOrOperatorNeverMatches() {
        assertThat(dropsOn(condition(null, "eq", "low"), payload("severity", "low"))).isFalse();
        assertThat(dropsOn(condition("severity", null, "low"), payload("severity", "low"))).isFalse();
    }

    // --- dotted-path lookup --------------------------------------------------------------------

    @Test
    void dottedPathResolvesNestedValues() {
        Map<String, Object> nested = payload("host", Map.of("name", "node-1"));

        assertThat(dropsOn(condition("host.name", "eq", "node-1"), nested)).isTrue();
    }

    @Test
    void dottedPathReturnsNullWhenAnIntermediateSegmentIsNotAMap() {
        Map<String, Object> flat = payload("host", "node-1");

        assertThat(dropsOn(condition("host.name", "exists", null), flat)).isFalse();
    }

    @Test
    void dottedPathReturnsNullForAMissingLeaf() {
        Map<String, Object> nested = payload("host", Map.of("name", "node-1"));

        assertThat(dropsOn(condition("host.fqdn", "exists", null), nested)).isFalse();
    }

    @Test
    void missingTopLevelFieldResolvesToNull() {
        assertThat(dropsOn(condition("absent", "exists", null), payload("severity", "low"))).isFalse();
    }

    // --- raw map round trip --------------------------------------------------------------------

    @Test
    void asMapReturnsTheRulesAsStoredSoTheJsonbColumnIsUnchanged() {
        Map<String, Object> raw = Map.of("enabled", true, "drop_if", List.of(condition("severity", "eq", "low")));

        assertThat(FilterRules.of(raw).asMap()).isEqualTo(raw);
    }

    @Test
    void asMapOfNullRulesIsAnEmptyMap() {
        assertThat(FilterRules.of(null).asMap()).isEmpty();
    }

    private static boolean dropsOn(Map<String, Object> condition, Map<String, Object> payload) {
        return FilterRules.of(Map.of("drop_if", List.of(condition))).shouldDrop(payload);
    }

    private static Map<String, Object> condition(String field, String op, Object value) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("field", field);
        condition.put("op", op);
        condition.put("value", value);
        return condition;
    }

    private static Map<String, Object> payload(Object... keyValuePairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            payload.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return payload;
    }
}
