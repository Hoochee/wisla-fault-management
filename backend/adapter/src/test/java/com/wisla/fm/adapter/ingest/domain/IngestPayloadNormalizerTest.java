package com.wisla.fm.adapter.ingest.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors the assertions of the {@code IngestPayloadMapper} characterization test against the
 * domain API, which takes {@code adapterVersion} and a {@link Clock} as parameters instead of
 * injecting {@code AdapterProperties} and calling {@code Instant.now()}.
 */
class IngestPayloadNormalizerTest {

    private static final String ADAPTER_VERSION = "9.9.9-characterization";
    private static final Instant FIXED_NOW = Instant.parse("2026-07-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    // --- probe / heartbeat branch -------------------------------------------------------------

    @Test
    void probeBooleanTrueProducesHeartbeatBodyWithoutEvents() {
        Map<String, Object> request = normalize(payload(
                "probe", true,
                "event_id", "ignored-when-probing"
        ));

        assertThat(request).containsOnlyKeys("heartbeat", "adapterVersion", "receivedAt");
        assertThat(request).containsEntry("heartbeat", true);
        assertThat(request).containsEntry("adapterVersion", ADAPTER_VERSION);
        assertThat(request).containsEntry("receivedAt", FIXED_NOW.toString());
    }

    /** Only the Boolean {@code true} triggers the heartbeat branch; the string "true" does not. */
    @Test
    void probeAsStringDoesNotTriggerHeartbeatBranch() {
        Map<String, Object> request = normalize(payload("probe", "true"));

        assertThat(request).containsKey("events");
        assertThat(request).doesNotContainKey("heartbeat");
    }

    @Test
    void eventRequestCarriesAdapterVersionAndReceivedAt() {
        Map<String, Object> request = normalize(payload("title", "Disk full"));

        assertThat(request).containsOnlyKeys("events", "adapterVersion", "receivedAt");
        assertThat(request).containsEntry("adapterVersion", ADAPTER_VERSION);
        assertThat(request).containsEntry("receivedAt", FIXED_NOW.toString());
        assertThat(events(request)).hasSize(1);
    }

    @Test
    void receivedAtComesFromTheSuppliedClockNotWallClockTime() {
        Instant before = Instant.now();
        Map<String, Object> request = IngestPayloadNormalizer.normalize(
                payload("title", "t"), ADAPTER_VERSION, Clock.systemUTC());
        Instant after = Instant.now();

        assertThat(Instant.parse((String) request.get("receivedAt"))).isBetween(before, after);
    }

    // --- event_nseverity switch (1..5 and the default) -----------------------------------------

    @ParameterizedTest
    @CsvSource({
            "5, fatal",
            "4, critical",
            "3, major",
            "2, warning",
            "1, minor",
            "0, minor",
            "6, minor",
            "not-a-number, minor"
    })
    void zabbixNseverityIsMappedThroughSeverityNormalization(String nseverity, String expected) {
        Map<String, Object> event = firstEvent(normalize(payload("event_nseverity", nseverity)));

        assertThat(event).containsEntry("severity", expected);
    }

    @Test
    void nseverityIsReadViaStringValueOfSoNumbersWorkToo() {
        Map<String, Object> event = firstEvent(normalize(payload("event_nseverity", 5)));

        assertThat(event).containsEntry("severity", "fatal");
    }

    @Test
    void nseverityWinsOverTextualSeverityFields() {
        Map<String, Object> event = firstEvent(normalize(payload(
                "event_nseverity", 1,
                "trigger_severity", "disaster",
                "severity", "disaster",
                "priority", "disaster"
        )));

        assertThat(event).containsEntry("severity", "minor");
    }

    // --- severity fallback chain and normalization table ---------------------------------------

    @Test
    void severityFallbackPrefersTriggerSeverityThenSeverityThenPriority() {
        assertThat(firstEvent(normalize(payload(
                "trigger_severity", "high",
                "severity", "warning",
                "priority", "disaster"
        )))).containsEntry("severity", "critical");

        assertThat(firstEvent(normalize(payload(
                "severity", "warning",
                "priority", "disaster"
        )))).containsEntry("severity", "warning");

        assertThat(firstEvent(normalize(payload(
                "priority", "disaster"
        )))).containsEntry("severity", "fatal");
    }

    /** No severity field at all falls back to the literal "info", which normalizes to "minor". */
    @Test
    void missingSeverityFallsBackToInfoAndThereforeMinor() {
        Map<String, Object> event = firstEvent(normalize(payload("title", "No severity here")));

        assertThat(event).containsEntry("severity", "minor");
    }

    @Test
    void blankSeverityFieldsAreSkippedInTheFallbackChain() {
        Map<String, Object> event = firstEvent(normalize(payload(
                "trigger_severity", "   ",
                "severity", "high"
        )));

        assertThat(event).containsEntry("severity", "critical");
    }

    @ParameterizedTest
    @CsvSource({
            "disaster, fatal",
            "fatal, fatal",
            "critical, critical",
            "high, critical",
            "average, major",
            "major, major",
            "warning, warning",
            "information, minor",
            "info, minor",
            "minor, minor",
            "not classified, minor",
            "low, minor",
            "normal, normal",
            "anything-else, minor",
            "DISASTER, fatal",
            "High, critical"
    })
    void severityNormalizationTableIsCaseInsensitive(String raw, String expected) {
        Map<String, Object> event = firstEvent(normalize(payload("severity", raw)));

        assertThat(event).containsEntry("severity", expected);
    }

    // --- event_value = 0 (Zabbix recovery) -----------------------------------------------------

    @Test
    void eventValueZeroForcesClosedStatus() {
        assertThat(firstEvent(normalize(payload("event_value", 0))))
                .containsEntry("status", "closed");
        assertThat(firstEvent(normalize(payload("event_value", "0"))))
                .containsEntry("status", "closed");
    }

    /** Recovery overrides an explicit status in the webhook payload. */
    @Test
    void eventValueZeroOverridesPayloadStatus() {
        Map<String, Object> event = firstEvent(normalize(payload(
                "event_value", 0,
                "status", "new"
        )));

        assertThat(event).containsEntry("status", "closed");
    }

    @Test
    void nonZeroEventValueKeepsPayloadStatus() {
        Map<String, Object> event = firstEvent(normalize(payload(
                "event_value", 1,
                "status", "in_progress"
        )));

        assertThat(event).containsEntry("status", "in_progress");
    }

    @Test
    void statusIsOmittedWhenAbsentOrBlank() {
        assertThat(firstEvent(normalize(payload("title", "No status"))))
                .doesNotContainKey("status");
        assertThat(firstEvent(normalize(payload("status", "   "))))
                .doesNotContainKey("status");
    }

    // --- externalId field-priority chain -------------------------------------------------------

    @Test
    void externalIdPrefersEventIdThenEventIdCamelThenIdThenAlertname() {
        assertThat(firstEvent(normalize(payload(
                "event_id", "a", "eventId", "b", "id", "c", "alertname", "d"
        )))).containsEntry("externalId", "a");

        assertThat(firstEvent(normalize(payload(
                "eventId", "b", "id", "c", "alertname", "d"
        )))).containsEntry("externalId", "b");

        assertThat(firstEvent(normalize(payload(
                "id", "c", "alertname", "d"
        )))).containsEntry("externalId", "c");

        assertThat(firstEvent(normalize(payload(
                "alertname", "d"
        )))).containsEntry("externalId", "d");
    }

    @Test
    void externalIdFallsBackToARandomUuid() {
        String first = (String) firstEvent(normalize(payload("title", "t"))).get("externalId");
        String second = (String) firstEvent(normalize(payload("title", "t"))).get("externalId");

        assertThat(UUID.fromString(first)).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    // --- title field-priority chain ------------------------------------------------------------

    @Test
    void titlePrefersTriggerNameThenProblemThenTitleThenSummaryThenAlertnameThenMessage() {
        assertThat(firstEvent(normalize(payload(
                "trigger_name", "t1", "problem", "p", "title", "ti",
                "summary", "s", "alertname", "a", "message", "m"
        )))).containsEntry("title", "t1");

        assertThat(firstEvent(normalize(payload(
                "problem", "p", "title", "ti", "summary", "s", "alertname", "a", "message", "m"
        )))).containsEntry("title", "p");

        assertThat(firstEvent(normalize(payload(
                "title", "ti", "summary", "s", "alertname", "a", "message", "m"
        )))).containsEntry("title", "ti");

        assertThat(firstEvent(normalize(payload(
                "summary", "s", "alertname", "a", "message", "m"
        )))).containsEntry("title", "s");

        assertThat(firstEvent(normalize(payload(
                "alertname", "a", "message", "m"
        )))).containsEntry("title", "a");

        assertThat(firstEvent(normalize(payload(
                "message", "m"
        )))).containsEntry("title", "m");
    }

    @Test
    void titleFallsBackToWebhookEvent() {
        assertThat(firstEvent(normalize(payload("severity", "high"))))
                .containsEntry("title", "Webhook event");
    }

    @Test
    void fieldLookupTrimsSurroundingWhitespace() {
        assertThat(firstEvent(normalize(payload("title", "  padded title  "))))
                .containsEntry("title", "padded title");
    }

    // --- description field-priority chain ------------------------------------------------------

    @Test
    void descriptionPrefersMessageThenDescriptionThenProblem() {
        assertThat(firstEvent(normalize(payload(
                "message", "m", "description", "d", "problem", "p"
        )))).containsEntry("description", "m");

        assertThat(firstEvent(normalize(payload(
                "description", "d", "problem", "p"
        )))).containsEntry("description", "d");
    }

    /** "problem" feeds both the title and the description chain. */
    @Test
    void problemAloneBecomesBothTitleAndDescription() {
        Map<String, Object> event = firstEvent(normalize(payload("problem", "p")));

        assertThat(event).containsEntry("title", "p");
        assertThat(event).containsEntry("description", "p");
    }

    @Test
    void descriptionIsOmittedWhenNoSourceFieldIsPresent() {
        assertThat(firstEvent(normalize(payload("title", "t"))))
                .doesNotContainKey("description");
    }

    // --- occurredAt parsing --------------------------------------------------------------------

    @Test
    void occurredAtPrefersEventTimeOverTheLaterFields() {
        Map<String, Object> event = firstEvent(normalize(payload(
                "event_time", "2026-01-01T00:00:00Z",
                "occurredAt", "2026-02-02T00:00:00Z",
                "timestamp", "2026-03-03T00:00:00Z",
                "eventTime", "2026-04-04T00:00:00Z",
                "time", "2026-05-05T00:00:00Z"
        )));

        assertThat(event).containsEntry("occurredAt", "2026-01-01T00:00:00Z");
    }

    @ParameterizedTest
    @CsvSource({
            "occurredAt, 2026-02-02T00:00:00Z",
            "timestamp, 2026-03-03T00:00:00Z",
            "eventTime, 2026-04-04T00:00:00Z",
            "time, 2026-05-05T00:00:00Z"
    })
    void occurredAtWalksTheWholeFieldChain(String field, String value) {
        Map<String, Object> event = firstEvent(normalize(payload(field, value)));

        assertThat(event).containsEntry("occurredAt", value);
    }

    /** An unparseable value does not stop the chain — the next field is tried. */
    @Test
    void unparseableOccurredAtCandidateFallsThroughToTheNextField() {
        Map<String, Object> event = firstEvent(normalize(payload(
                "event_time", "1700000000",
                "occurredAt", "2026-02-02T00:00:00Z"
        )));

        assertThat(event).containsEntry("occurredAt", "2026-02-02T00:00:00Z");
    }

    @Test
    void occurredAtFallsBackToTheClockWhenNothingParses() {
        Map<String, Object> event = firstEvent(normalize(payload(
                "event_time", "not-a-timestamp",
                "time", "2026/05/05 10:00:00"
        )));

        assertThat(event).containsEntry("occurredAt", FIXED_NOW.toString());
    }

    // --- nodeFqdn field-priority chain --------------------------------------------------------

    @Test
    void nodeFqdnPrefersHostnameThenHostThenNodeThenInstanceThenNodeFqdn() {
        assertThat(firstEvent(normalize(payload(
                "hostname", "h1", "host", "h2", "node", "h3", "instance", "h4", "nodeFqdn", "h5"
        )))).containsEntry("nodeFqdn", "h1");

        assertThat(firstEvent(normalize(payload(
                "host", "h2", "node", "h3", "instance", "h4", "nodeFqdn", "h5"
        )))).containsEntry("nodeFqdn", "h2");

        assertThat(firstEvent(normalize(payload(
                "node", "h3", "instance", "h4", "nodeFqdn", "h5"
        )))).containsEntry("nodeFqdn", "h3");

        assertThat(firstEvent(normalize(payload(
                "instance", "h4", "nodeFqdn", "h5"
        )))).containsEntry("nodeFqdn", "h4");

        assertThat(firstEvent(normalize(payload(
                "nodeFqdn", "h5"
        )))).containsEntry("nodeFqdn", "h5");
    }

    @Test
    void nodeFqdnIsOmittedWhenNoHostFieldIsPresent() {
        assertThat(firstEvent(normalize(payload("title", "t"))))
                .doesNotContainKey("nodeFqdn");
    }

    // --- attributes / rawPayload and the produced field set -----------------------------------

    @Test
    void attributesAndRawPayloadBothCarryTheWholeWebhookPayload() {
        Map<String, Object> webhookPayload = payload("title", "t", "severity", "high");

        Map<String, Object> event = firstEvent(normalize(webhookPayload));

        assertThat(event.get("attributes")).isSameAs(webhookPayload);
        assertThat(event.get("rawPayload")).isSameAs(webhookPayload);
    }

    @Test
    void fullPayloadProducesExactlyTheKnownEventFields() {
        Map<String, Object> event = firstEvent(normalize(payload(
                "event_id", "evt-1",
                "trigger_name", "Disk full",
                "message", "Disk is at 95%",
                "event_nseverity", 4,
                "event_value", 1,
                "status", "new",
                "event_time", "2026-01-01T00:00:00Z",
                "hostname", "node-1.example"
        )));

        assertThat(event).containsOnlyKeys(
                "externalId", "title", "description", "severity", "status",
                "occurredAt", "nodeFqdn", "attributes", "rawPayload"
        );
    }

    @Test
    void minimalPayloadOmitsEveryOptionalEventField() {
        Map<String, Object> event = firstEvent(normalize(payload("severity", "high")));

        assertThat(event).containsOnlyKeys(
                "externalId", "title", "severity", "occurredAt", "attributes", "rawPayload"
        );
    }

    private static Map<String, Object> normalize(Map<String, Object> webhookPayload) {
        return IngestPayloadNormalizer.normalize(webhookPayload, ADAPTER_VERSION, FIXED_CLOCK);
    }

    private static Map<String, Object> payload(Object... keyValuePairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            payload.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> events(Map<String, Object> request) {
        return (List<Map<String, Object>>) request.get("events");
    }

    private static Map<String, Object> firstEvent(Map<String, Object> request) {
        return events(request).getFirst();
    }
}
