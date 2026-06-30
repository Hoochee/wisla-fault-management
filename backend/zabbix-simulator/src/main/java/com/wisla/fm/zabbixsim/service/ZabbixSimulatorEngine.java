package com.wisla.fm.zabbixsim.service;

import com.wisla.fm.zabbixsim.config.SimulatorRuntimeConfig;
import com.wisla.fm.zabbixsim.model.ZabbixScenario;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ZabbixSimulatorEngine {

    private final SimulatorRuntimeConfig runtimeConfig;
    private final ZabbixScenarioCatalog catalog;
    private final AdapterWebhookClient adapterWebhookClient;

    public ZabbixSimulatorEngine(
            SimulatorRuntimeConfig runtimeConfig,
            ZabbixScenarioCatalog catalog,
            AdapterWebhookClient adapterWebhookClient
    ) {
        this.runtimeConfig = runtimeConfig;
        this.catalog = catalog;
        this.adapterWebhookClient = adapterWebhookClient;
    }

    public SimulationResult tick() {
        if (!runtimeConfig.isEnabled()) {
            return SimulationResult.skipped("simulator disabled");
        }
        if (catalog.activeCount() > 0 && ThreadLocalRandom.current().nextDouble() < runtimeConfig.recoveryProbability()) {
            return sendRecovery();
        }
        return sendRandomProblem();
    }

    public SimulationResult fireScenario(String scenarioId, boolean recovery) {
        Optional<ZabbixScenario> scenario = catalog.findById(scenarioId);
        if (scenario.isEmpty()) {
            return SimulationResult.failed("unknown scenario: " + scenarioId);
        }
        if (recovery) {
            catalog.markProblem(scenario.get(), catalog.nextEventId());
            return sendRecoveryFor(scenario.get());
        }
        return sendProblem(scenario.get());
    }

    private SimulationResult sendRecovery() {
        Optional<ZabbixScenarioCatalog.ActiveProblem> active = catalog.pickRecovery();
        if (active.isEmpty()) {
            return sendRandomProblem();
        }
        ZabbixScenarioCatalog.ActiveProblem problem = active.get();
        Map<String, Object> payload = problem.scenario().toRecoveryPayload(runtimeConfig.zabbixUrl(), problem.eventId());
        AdapterWebhookClient.DeliveryResult result = adapterWebhookClient.send(payload);
        if (result.success()) {
            catalog.clearProblem(problem.scenario().id());
        }
        return SimulationResult.of("recovery", problem.scenario().id(), payload, result);
    }

    private SimulationResult sendRecoveryFor(ZabbixScenario scenario) {
        var active = catalog.pickRecovery().filter(p -> p.scenario().id().equals(scenario.id()));
        long eventId = active.map(ZabbixScenarioCatalog.ActiveProblem::eventId).orElseGet(catalog::nextEventId);
        Map<String, Object> payload = scenario.toRecoveryPayload(runtimeConfig.zabbixUrl(), eventId);
        AdapterWebhookClient.DeliveryResult result = adapterWebhookClient.send(payload);
        if (result.success()) {
            catalog.clearProblem(scenario.id());
        }
        return SimulationResult.of("recovery", scenario.id(), payload, result);
    }

    private SimulationResult sendRandomProblem() {
        var list = catalog.all();
        ZabbixScenario scenario = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        return sendProblem(scenario);
    }

    private SimulationResult sendProblem(ZabbixScenario scenario) {
        long eventId = catalog.nextEventId();
        Map<String, Object> payload = scenario.toProblemPayload(runtimeConfig.zabbixUrl(), eventId);
        AdapterWebhookClient.DeliveryResult result = adapterWebhookClient.send(payload);
        if (result.success()) {
            catalog.markProblem(scenario, eventId);
        }
        return SimulationResult.of("problem", scenario.id(), payload, result);
    }

    public record SimulationResult(
            String kind,
            String scenarioId,
            Map<String, Object> payload,
            boolean delivered,
            Integer httpStatus,
            String error,
            String note,
            String deliveryTarget,
            String adapterWebhookUrl
    ) {
        static SimulationResult of(
                String kind,
                String scenarioId,
                Map<String, Object> payload,
                AdapterWebhookClient.DeliveryResult result
        ) {
            return new SimulationResult(
                    kind,
                    scenarioId,
                    payload,
                    result.success(),
                    result.httpStatus(),
                    result.error(),
                    result.success() ? "adapter accepted; fm-module ingest handled by adapter" : null,
                    "adapter",
                    result.adapterWebhookUrl()
            );
        }

        static SimulationResult skipped(String note) {
            return new SimulationResult("skip", null, Map.of(), false, null, null, note, null, null);
        }

        static SimulationResult failed(String error) {
            return new SimulationResult("error", null, Map.of(), false, null, error, null, null, null);
        }
    }
}
