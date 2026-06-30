package com.wisla.fm.zabbixsim.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payload shaped like Zabbix 6.x Media Type "Webhook" default JSON body
 * (macros expanded — see docs/zabbix-simulator.md).
 */
public record ZabbixScenario(
        String id,
        String host,
        String hostIp,
        String triggerId,
        String triggerName,
        String triggerSeverity,
        int eventNseverity,
        String itemKey,
        String itemValueTemplate,
        String messageTemplate
) {
    public Map<String, Object> toProblemPayload(String zabbixUrl, long eventId) {
        Map<String, Object> body = baseFields(zabbixUrl, eventId);
        body.put("event_value", "1");
        body.put("event_update_status", "0");
        body.put("item_value", itemValueTemplate);
        body.put("message", messageTemplate.replace("{HOST}", host).replace("{VALUE}", itemValueTemplate));
        body.put("problem", triggerName);
        return body;
    }

    public Map<String, Object> toRecoveryPayload(String zabbixUrl, long eventId) {
        Map<String, Object> body = baseFields(zabbixUrl, eventId);
        body.put("event_value", "0");
        body.put("event_update_status", "1");
        body.put("item_value", "0");
        body.put("message", "Resolved: " + triggerName + " on " + host);
        body.put("problem", "Resolved: " + triggerName);
        body.put("status", "resolved");
        return body;
    }

    private Map<String, Object> baseFields(String zabbixUrl, long eventId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("host", host);
        body.put("hostname", host);
        body.put("host_ip", hostIp);
        body.put("trigger_id", triggerId);
        body.put("trigger_name", triggerName);
        body.put("trigger_severity", triggerSeverity);
        body.put("event_id", String.valueOf(eventId));
        body.put("event_nseverity", String.valueOf(eventNseverity));
        body.put("item_key", itemKey);
        body.put("zabbix_url", zabbixUrl);
        body.put("event_time", java.time.Instant.now().toString());
        body.put("tags", Map.of("source", "zabbix-simulator", "scenario", id));
        return body;
    }
}
