package ru.wisla.fm.testsupport;

import ru.wisla.fm.adapters.client.SimulatorControlResponse;
import ru.wisla.fm.adapters.client.SimulatorHealthResponse;
import ru.wisla.fm.adapters.client.SimulatorRuntimeConfigRequest;
import ru.wisla.fm.adapters.client.SimulatorRuntimeConfigResponse;
import ru.wisla.fm.adapters.client.SimulatorTickResponse;
import ru.wisla.fm.adapters.client.ZabbixSimulatorClient;

import java.util.function.Function;

public class TestZabbixSimulatorClient implements ZabbixSimulatorClient {

  private Function<SimulatorRuntimeConfigRequest, SimulatorRuntimeConfigResponse> configHandler =
      request ->
          new SimulatorRuntimeConfigResponse(
              true,
              "http://localhost:18081/webhook/" + request.sourceWebhookKey(),
              request.sourceWebhookKey(),
              true,
              "configured");

  private SimulatorHealthResponse healthResponse =
      new SimulatorHealthResponse("ok", true, null, null, 0);

  private SimulatorTickResponse tickResponse =
      new SimulatorTickResponse("problem", "cpu-high", true, 202, null, "adapter accepted");

  private SimulatorControlResponse controlResponse =
      new SimulatorControlResponse(true, "auto-tick enabled");

  public void setConfigHandler(
      Function<SimulatorRuntimeConfigRequest, SimulatorRuntimeConfigResponse> configHandler) {
    this.configHandler = configHandler;
  }

  public void setHealthResponse(SimulatorHealthResponse healthResponse) {
    this.healthResponse = healthResponse;
  }

  public void setTickResponse(SimulatorTickResponse tickResponse) {
    this.tickResponse = tickResponse;
  }

  public void setControlResponse(SimulatorControlResponse controlResponse) {
    this.controlResponse = controlResponse;
  }

  @Override
  public SimulatorHealthResponse fetchHealth() {
    return healthResponse;
  }

  @Override
  public SimulatorRuntimeConfigResponse applyConfig(SimulatorRuntimeConfigRequest request) {
    return configHandler.apply(request);
  }

  @Override
  public SimulatorTickResponse tick() {
    return tickResponse;
  }

  @Override
  public SimulatorControlResponse setControl(boolean enabled) {
    return new SimulatorControlResponse(
        enabled,
        enabled ? controlResponse.message() : "auto-tick disabled");
  }
}
