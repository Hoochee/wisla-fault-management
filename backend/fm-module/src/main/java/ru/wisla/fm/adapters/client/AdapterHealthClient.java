package ru.wisla.fm.adapters.client;

import java.util.Optional;

public interface AdapterHealthClient {

    Optional<AdapterHealthResponse> fetchHealth();
}
