package com.wisla.fm.adapter.ingest.testsupport;

import com.wisla.fm.adapter.ingest.application.port.out.ApiKeyVerifierPort;

/**
 * Stands in for the BCrypt verifier: a hash is the raw key prefixed with {@value #HASH_PREFIX}.
 */
public final class PrefixingApiKeyVerifier implements ApiKeyVerifierPort {

    public static final String HASH_PREFIX = "hash:";

    public static String hash(String rawKey) {
        return HASH_PREFIX + rawKey;
    }

    @Override
    public boolean matches(String rawKey, String storedHash) {
        return storedHash != null && storedHash.equals(hash(rawKey));
    }
}
