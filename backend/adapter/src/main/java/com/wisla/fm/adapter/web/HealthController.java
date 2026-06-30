package com.wisla.fm.adapter.web;

import com.wisla.fm.adapter.config.AdapterProperties;
import com.wisla.fm.adapter.service.HealthService;
import com.wisla.fm.adapter.web.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> getHealth() {
        return healthService.getHealth();
    }
}
