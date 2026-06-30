package com.wisla.fm.zabbixsim.scheduler;

import com.wisla.fm.zabbixsim.config.SimulatorProperties;
import com.wisla.fm.zabbixsim.config.SimulatorRuntimeConfig;
import com.wisla.fm.zabbixsim.service.ZabbixSimulatorEngine;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class AlertScheduler {

    private final SimulatorProperties properties;
    private final SimulatorRuntimeConfig runtimeConfig;
    private final ZabbixSimulatorEngine engine;

    public AlertScheduler(
            SimulatorProperties properties,
            SimulatorRuntimeConfig runtimeConfig,
            ZabbixSimulatorEngine engine
    ) {
        this.properties = properties;
        this.runtimeConfig = runtimeConfig;
        this.engine = engine;
    }

    @Scheduled(fixedDelayString = "${wisla.zabbix-simulator.interval-sec:90}000",
            initialDelayString = "${wisla.zabbix-simulator.initial-delay-sec:30}000")
    public void scheduledTick() {
        if (!runtimeConfig.isEnabled()) {
            return;
        }
        int jitter = properties.jitterSec() > 0
                ? ThreadLocalRandom.current().nextInt(-properties.jitterSec(), properties.jitterSec() + 1)
                : 0;
        if (jitter > 0) {
            try {
                Thread.sleep(jitter * 1000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        engine.tick();
    }
}
