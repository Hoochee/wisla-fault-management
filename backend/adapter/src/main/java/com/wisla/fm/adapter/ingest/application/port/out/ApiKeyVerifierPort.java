package com.wisla.fm.adapter.ingest.application.port.out;

public interface ApiKeyVerifierPort {

    boolean matches(String rawKey, String storedHash);
}
