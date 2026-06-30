package ru.wisla.fm.adapters.client;

import org.springframework.web.client.RestClient;

import java.util.Optional;

public class RestAdapterHealthClient implements AdapterHealthClient {

    private final RestClient adapterRestClient;

    public RestAdapterHealthClient(RestClient adapterRestClient) {
        this.adapterRestClient = adapterRestClient;
    }

    @Override
    public Optional<AdapterHealthResponse> fetchHealth() {
        try {
            AdapterHealthResponse body = adapterRestClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(AdapterHealthResponse.class);
            return Optional.ofNullable(body);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
