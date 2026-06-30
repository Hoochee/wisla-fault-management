package ru.wisla.fm.adapters.client;

public interface ZabbixSimulatorClient {

    SimulatorHealthResponse fetchHealth();

    SimulatorRuntimeConfigResponse applyConfig(SimulatorRuntimeConfigRequest request);

    SimulatorTickResponse tick();

    SimulatorControlResponse setControl(boolean enabled);
}
