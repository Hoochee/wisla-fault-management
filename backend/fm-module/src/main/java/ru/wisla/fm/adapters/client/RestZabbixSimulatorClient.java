package ru.wisla.fm.adapters.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.wisla.fm.common.api.UpstreamException;

public class RestZabbixSimulatorClient implements ZabbixSimulatorClient {

    private final RestClient zabbixSimulatorRestClient;

    public RestZabbixSimulatorClient(RestClient zabbixSimulatorRestClient) {
        this.zabbixSimulatorRestClient = zabbixSimulatorRestClient;
    }

    @Override
    public SimulatorHealthResponse fetchHealth() {
        try {
            SimulatorHealthResponse body = zabbixSimulatorRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(SimulatorHealthResponse.class);
            if (body == null) {
                throw new UpstreamException(
                        "simulator_unreachable",
                        "zabbix-simulator health returned empty body",
                        HttpStatus.BAD_GATEWAY
                );
            }
            return body;
        } catch (RestClientException ex) {
            throw new UpstreamException(
                    "simulator_unreachable",
                    "zabbix-simulator unreachable: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    @Override
    public SimulatorRuntimeConfigResponse applyConfig(SimulatorRuntimeConfigRequest request) {
        try {
            SimulatorRuntimeConfigResponse body = zabbixSimulatorRestClient.post()
                    .uri("/config")
                    .body(request)
                    .retrieve()
                    .body(SimulatorRuntimeConfigResponse.class);
            if (body == null) {
                throw new UpstreamException(
                        "simulator_unreachable",
                        "zabbix-simulator /config returned empty body",
                        HttpStatus.BAD_GATEWAY
                );
            }
            return body;
        } catch (RestClientException ex) {
            throw new UpstreamException(
                    "simulator_unreachable",
                    "zabbix-simulator /config failed: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    @Override
    public SimulatorTickResponse tick() {
        try {
            SimulatorTickResponse body = zabbixSimulatorRestClient.post()
                    .uri("/tick")
                    .retrieve()
                    .body(SimulatorTickResponse.class);
            if (body == null) {
                throw new UpstreamException(
                        "simulator_unreachable",
                        "zabbix-simulator /tick returned empty body",
                        HttpStatus.BAD_GATEWAY
                );
            }
            return body;
        } catch (RestClientException ex) {
            throw new UpstreamException(
                    "simulator_unreachable",
                    "zabbix-simulator /tick failed: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    @Override
    public SimulatorControlResponse setControl(boolean enabled) {
        try {
            SimulatorControlResponse body = zabbixSimulatorRestClient.post()
                    .uri("/control")
                    .body(new SimulatorControlRequest(enabled))
                    .retrieve()
                    .body(SimulatorControlResponse.class);
            if (body == null) {
                throw new UpstreamException(
                        "simulator_unreachable",
                        "zabbix-simulator /control returned empty body",
                        HttpStatus.BAD_GATEWAY
                );
            }
            return body;
        } catch (RestClientException ex) {
            throw new UpstreamException(
                    "simulator_unreachable",
                    "zabbix-simulator /control failed: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
    }
}
