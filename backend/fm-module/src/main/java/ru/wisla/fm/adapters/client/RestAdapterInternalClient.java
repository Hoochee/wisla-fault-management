package ru.wisla.fm.adapters.client;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.wisla.fm.common.api.UpstreamException;
import ru.wisla.fm.config.AdapterProperties;

public class RestAdapterInternalClient implements AdapterInternalClient {

    private final RestClient adapterRestClient;
    private final AdapterProperties adapterProperties;

    public RestAdapterInternalClient(RestClient adapterRestClient, AdapterProperties adapterProperties) {
        this.adapterRestClient = adapterRestClient;
        this.adapterProperties = adapterProperties;
    }

    @Override
    public void syncConfig() {
        try {
            adapterRestClient.post()
                    .uri("/internal/config/sync")
                    .headers(headers -> applyInternalAuth(headers))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new UpstreamException(
                    "adapter_unreachable",
                    "Adapter config sync failed: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    @Override
    public AdapterProbeResponse probe(AdapterProbeRequest request) {
        try {
            return adapterRestClient.post()
                    .uri("/internal/probe")
                    .headers(headers -> applyInternalAuth(headers))
                    .body(request)
                    .retrieve()
                    .body(AdapterProbeResponse.class);
        } catch (RestClientException ex) {
            throw new UpstreamException(
                    "adapter_unreachable",
                    "Adapter probe failed: " + ex.getMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private void applyInternalAuth(org.springframework.http.HttpHeaders headers) {
        String token = adapterProperties.internalToken();
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
    }
}
