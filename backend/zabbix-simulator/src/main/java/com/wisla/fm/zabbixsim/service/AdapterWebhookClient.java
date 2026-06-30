package com.wisla.fm.zabbixsim.service;

import com.wisla.fm.zabbixsim.config.SimulatorRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Delivers Zabbix-shaped webhook payloads to WISLA FM Adapter only.
 * Ingest into fm-module (mapping, filter, buffer, forward) is adapter responsibility.
 */
@Service
public class AdapterWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(AdapterWebhookClient.class);

    private final SimulatorRuntimeConfig runtimeConfig;
    private final RestClient restClient;

    public AdapterWebhookClient(SimulatorRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.restClient = RestClient.builder().build();
    }

    public DeliveryResult send(Map<String, Object> payload) {
        String targetUrl = runtimeConfig.resolvedAdapterWebhookUrl();
        try {
            var response = restClient.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header("X-Source-Key", runtimeConfig.getApiKey())
                    .header("User-Agent", "Zabbix/6.4.0")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            int status = response.getStatusCode().value();
            log.info(
                    "Adapter webhook accepted event_id={} status={} url={} (adapter forwards to fm-module)",
                    payload.get("event_id"),
                    status,
                    targetUrl
            );
            return new DeliveryResult(true, status, null, targetUrl);
        } catch (RestClientResponseException ex) {
            log.warn(
                    "Adapter webhook rejected event_id={} status={} url={} body={}",
                    payload.get("event_id"),
                    ex.getStatusCode().value(),
                    targetUrl,
                    ex.getResponseBodyAsString()
            );
            return new DeliveryResult(false, ex.getStatusCode().value(), ex.getResponseBodyAsString(), targetUrl);
        } catch (Exception ex) {
            log.warn("Adapter webhook failed event_id={} url={}: {}", payload.get("event_id"), targetUrl, ex.getMessage());
            return new DeliveryResult(false, null, ex.getMessage(), targetUrl);
        }
    }

    public record DeliveryResult(boolean success, Integer httpStatus, String error, String adapterWebhookUrl) {
    }
}
