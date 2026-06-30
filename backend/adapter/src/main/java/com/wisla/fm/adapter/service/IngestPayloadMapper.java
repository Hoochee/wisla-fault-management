package com.wisla.fm.adapter.service;



import com.wisla.fm.adapter.config.AdapterProperties;

import org.springframework.stereotype.Service;



import java.time.Instant;

import java.util.LinkedHashMap;

import java.util.List;

import java.util.Map;

import java.util.UUID;



@Service

public class IngestPayloadMapper {



    private final AdapterProperties properties;



    public IngestPayloadMapper(AdapterProperties properties) {

        this.properties = properties;

    }



    public Map<String, Object> toIngestRequest(Map<String, Object> webhookPayload) {

        if (Boolean.TRUE.equals(webhookPayload.get("probe"))) {

            Map<String, Object> request = new LinkedHashMap<>();

            request.put("heartbeat", true);

            request.put("adapterVersion", properties.version());

            request.put("receivedAt", Instant.now().toString());

            return request;

        }



        Map<String, Object> event = new LinkedHashMap<>();

        event.put("externalId", firstNonBlank(webhookPayload, "event_id", "eventId", "id", "alertname")

                .orElse(UUID.randomUUID().toString()));

        event.put("title", firstNonBlank(webhookPayload, "trigger_name", "problem", "title", "summary", "alertname", "message")

                .orElse("Webhook event"));

        firstNonBlank(webhookPayload, "message", "description", "problem")

                .ifPresent(value -> event.put("description", value));

        event.put("severity", normalizeSeverity(resolveSeverity(webhookPayload)));
        if (isZabbixRecovery(webhookPayload)) {
            event.put("status", "closed");
        } else {
            firstNonBlank(webhookPayload, "status").ifPresent(value -> event.put("status", value));
        }

        event.put("occurredAt", parseOccurredAt(webhookPayload).toString());

        firstNonBlank(webhookPayload, "hostname", "host", "node", "instance", "nodeFqdn")

                .ifPresent(value -> event.put("nodeFqdn", value));

        event.put("attributes", webhookPayload);

        event.put("rawPayload", webhookPayload);



        Map<String, Object> request = new LinkedHashMap<>();

        request.put("events", List.of(event));

        request.put("adapterVersion", properties.version());

        request.put("receivedAt", Instant.now().toString());

        return request;

    }



    private boolean isZabbixRecovery(Map<String, Object> payload) {
        Object eventValue = payload.get("event_value");
        return eventValue != null && "0".equals(String.valueOf(eventValue));
    }



    private String resolveSeverity(Map<String, Object> payload) {

        Object nseverity = payload.get("event_nseverity");

        if (nseverity != null) {

            return switch (String.valueOf(nseverity)) {

                case "5" -> "disaster";

                case "4" -> "high";

                case "3" -> "average";

                case "2" -> "warning";

                case "1" -> "information";

                default -> "not classified";

            };

        }

        return firstNonBlank(payload, "trigger_severity", "severity", "priority").orElse("info");

    }



    private Instant parseOccurredAt(Map<String, Object> payload) {

        for (String field : List.of("event_time", "occurredAt", "timestamp", "eventTime", "time")) {

            Object value = payload.get(field);

            if (value == null) {

                continue;

            }

            try {

                return Instant.parse(String.valueOf(value));

            } catch (Exception ignored) {

                // try next field

            }

        }

        return Instant.now();

    }



    private String normalizeSeverity(String severity) {

        return switch (severity.toLowerCase()) {

            case "disaster", "fatal" -> "fatal";

            case "critical", "high" -> "critical";

            case "average", "major" -> "major";

            case "warning" -> "warning";

            case "information", "info", "minor", "not classified", "low" -> "minor";

            case "normal" -> "normal";

            default -> "minor";

        };

    }



    private java.util.Optional<String> firstNonBlank(Map<String, Object> payload, String... fields) {

        for (String field : fields) {

            Object value = payload.get(field);

            if (value != null) {

                String text = String.valueOf(value).trim();

                if (!text.isEmpty()) {

                    return java.util.Optional.of(text);

                }

            }

        }

        return java.util.Optional.empty();

    }

}


