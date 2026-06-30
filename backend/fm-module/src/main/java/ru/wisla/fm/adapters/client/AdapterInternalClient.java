package ru.wisla.fm.adapters.client;

public interface AdapterInternalClient {

    void syncConfig();

    AdapterProbeResponse probe(AdapterProbeRequest request);
}
