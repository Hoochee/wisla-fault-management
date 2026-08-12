package com.wisla.fm.adapter.ingest.adapter.out.crypto;

import com.wisla.fm.adapter.ingest.application.port.out.ApiKeyVerifierPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordEncoderApiKeyVerifier implements ApiKeyVerifierPort {

    private final PasswordEncoder passwordEncoder;

    public PasswordEncoderApiKeyVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean matches(String rawKey, String storedHash) {
        return passwordEncoder.matches(rawKey, storedHash);
    }
}
