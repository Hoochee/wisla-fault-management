package com.wisla.fm.zabbixsim.service;

import com.wisla.fm.zabbixsim.config.SimulatorProperties;
import com.wisla.fm.zabbixsim.model.ZabbixScenario;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ZabbixScenarioCatalog {

    private final AtomicLong eventIdSeq;
    private final ConcurrentHashMap<String, ActiveProblem> activeProblems = new ConcurrentHashMap<>();

    public ZabbixScenarioCatalog(SimulatorProperties properties) {
        this.eventIdSeq = new AtomicLong(Math.max(0, properties.eventIdStart()));
    }

    private final List<ZabbixScenario> scenarios = List.of(
            new ZabbixScenario(
                    "cpu-high",
                    "demo-server.wisla.local",
                    "10.10.1.21",
                    "10001",
                    "High CPU utilization (>90% for 5m)",
                    "High",
                    4,
                    "system.cpu.util[,idle]",
                    "92.4",
                    "PROBLEM: CPU idle below 10% on {HOST}. Last value: {VALUE}%"
            ),
            new ZabbixScenario(
                    "disk-space",
                    "db-prod-01.moscow.company.ru",
                    "10.10.2.15",
                    "10002",
                    "Disk space is critically low on /var",
                    "Disaster",
                    5,
                    "vfs.fs.size[/var,pused]",
                    "96.8",
                    "PROBLEM: Filesystem /var usage {VALUE}% on {HOST}"
            ),
            new ZabbixScenario(
                    "service-down",
                    "web-app-03.moscow.company.ru",
                    "10.10.3.44",
                    "10003",
                    "Zabbix agent is not available",
                    "Average",
                    3,
                    "zabbix[host,agent,available]",
                    "0",
                    "PROBLEM: Zabbix agent unreachable on {HOST}"
            ),
            new ZabbixScenario(
                    "memory-warning",
                    "demo-server.wisla.local",
                    "10.10.1.21",
                    "10004",
                    "High memory utilization (>85%)",
                    "Warning",
                    2,
                    "vm.memory.size[pavailable]",
                    "12.1",
                    "PROBLEM: Available memory {VALUE}% on {HOST}"
            ),
            new ZabbixScenario(
                    "network-errors",
                    "core-sw-01.dc1.company.ru",
                    "10.10.0.5",
                    "10005",
                    "Network interface errors rate high",
                    "High",
                    4,
                    "net.if.in.errors[ifHCInOctets.1]",
                    "1240",
                    "PROBLEM: Interface errors/sec {VALUE} on {HOST}"
            )
    );

    public List<ZabbixScenario> all() {
        return scenarios;
    }

    public Optional<ZabbixScenario> findById(String id) {
        return scenarios.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public long nextEventId() {
        return eventIdSeq.incrementAndGet();
    }

    public void markProblem(ZabbixScenario scenario, long eventId) {
        activeProblems.put(scenario.id(), new ActiveProblem(scenario, eventId));
    }

    public Optional<ActiveProblem> pickRecovery() {
        if (activeProblems.isEmpty()) {
            return Optional.empty();
        }
        var values = List.copyOf(activeProblems.values());
        return Optional.of(values.get((int) (Math.random() * values.size())));
    }

    public void clearProblem(String scenarioId) {
        activeProblems.remove(scenarioId);
    }

    public int activeCount() {
        return activeProblems.size();
    }

    public record ActiveProblem(ZabbixScenario scenario, long eventId) {
    }
}
