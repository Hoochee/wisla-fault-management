package com.wisla.fm.adapter.ingest.testsupport;

import com.wisla.fm.adapter.ingest.application.port.out.FmModuleSourceConfigPort;

import java.util.List;
import java.util.function.Supplier;

public final class StubFmModuleSourceConfigPort implements FmModuleSourceConfigPort {

    private Supplier<List<RemoteSourceConfig>> response = List::of;
    private int callCount;

    public void returning(List<RemoteSourceConfig> sources) {
        this.response = () -> sources;
    }

    public void failingWith(RuntimeException failure) {
        this.response = () -> {
            throw failure;
        };
    }

    public int callCount() {
        return callCount;
    }

    @Override
    public List<RemoteSourceConfig> fetchSources() {
        callCount++;
        return response.get();
    }
}
