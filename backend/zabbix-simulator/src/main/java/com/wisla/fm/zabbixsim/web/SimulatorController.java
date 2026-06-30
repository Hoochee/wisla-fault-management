package com.wisla.fm.zabbixsim.web;

import com.wisla.fm.zabbixsim.config.SimulatorRuntimeConfig;
import com.wisla.fm.zabbixsim.service.ZabbixScenarioCatalog;
import com.wisla.fm.zabbixsim.service.ZabbixSimulatorEngine;
import com.wisla.fm.zabbixsim.web.dto.ControlRequest;
import com.wisla.fm.zabbixsim.web.dto.ControlResponse;
import com.wisla.fm.zabbixsim.web.dto.RuntimeConfigRequest;
import com.wisla.fm.zabbixsim.web.dto.RuntimeConfigResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class SimulatorController {

    private final SimulatorRuntimeConfig runtimeConfig;
    private final ZabbixScenarioCatalog catalog;
    private final ZabbixSimulatorEngine engine;

    public SimulatorController(
            SimulatorRuntimeConfig runtimeConfig,
            ZabbixScenarioCatalog catalog,
            ZabbixSimulatorEngine engine
    ) {
        this.runtimeConfig = runtimeConfig;
        this.catalog = catalog;
        this.engine = engine;
    }

    @PostMapping("/config")
    public RuntimeConfigResponse applyConfig(@Valid @RequestBody RuntimeConfigRequest request) {
        runtimeConfig.applySourceBinding(request.sourceWebhookKey(), request.apiKey());
        return new RuntimeConfigResponse(
                true,
                runtimeConfig.resolvedAdapterWebhookUrl(),
                runtimeConfig.getSourceWebhookKey(),
                runtimeConfig.isEnabled(),
                "runtime config applied"
        );
    }

    @PostMapping("/control")
    public ControlResponse setControl(@Valid @RequestBody ControlRequest request) {
        runtimeConfig.setEnabled(request.enabled());
        String message = request.enabled() ? "auto-tick enabled" : "auto-tick disabled";
        return new ControlResponse(runtimeConfig.isEnabled(), message);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "zabbix-simulator");
        body.put("enabled", runtimeConfig.isEnabled());
        body.put("adapter_base_url", runtimeConfig.getAdapterBaseUrl());
        body.put("source_webhook_key", runtimeConfig.getSourceWebhookKey());
        body.put("adapter_webhook_url", runtimeConfig.resolvedAdapterWebhookUrl());
        body.put("delivery_chain", List.of(
                "zabbix-simulator → adapter POST /webhook/{sourceKey}",
                "adapter → fm-module POST /api/v1/ingest (mapping, filter, buffer)"
        ));
        body.put("active_problems", catalog.activeCount());
        body.put("scenarios", catalog.all().size());
        return body;
    }

    @GetMapping("/scenarios")
    public List<Map<String, Object>> scenarios() {
        return catalog.all().stream().map(s -> Map.<String, Object>of(
                "id", s.id(),
                "host", s.host(),
                "trigger_name", s.triggerName(),
                "trigger_severity", s.triggerSeverity(),
                "event_nseverity", s.eventNseverity()
        )).toList();
    }

    @PostMapping("/tick")
    public ZabbixSimulatorEngine.SimulationResult tick() {
        return engine.tick();
    }

    @PostMapping("/scenarios/{id}/fire")
    public ZabbixSimulatorEngine.SimulationResult fire(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean recovery
    ) {
        return engine.fireScenario(id, recovery);
    }
}
