package com.wisla.fm.adapter.testsupport;

import com.wisla.fm.adapter.ingest.adapter.out.http.FmModuleClient;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class TestFmModuleClient extends FmModuleClient {

    private final AtomicReference<IngestResult> nextForwardResult = new AtomicReference<>(
            new IngestResult(true, 202, 1L, null, false)
    );

    public void stubForwardIngest(IngestResult result) {
        nextForwardResult.set(result);
    }

    public void resetForwardIngest() {
        nextForwardResult.set(new IngestResult(true, 202, 1L, null, false));
    }

    @Override
    public IngestResult forwardIngest(String baseUrl, String ingestApiKey, Map<String, Object> ingestBody) {
        return nextForwardResult.get();
    }
}
