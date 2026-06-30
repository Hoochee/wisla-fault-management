package com.wisla.fm.adapter.web;

import com.wisla.fm.adapter.config.AdapterProperties;
import com.wisla.fm.adapter.service.AdapterException;
import com.wisla.fm.adapter.service.ProbeService;
import com.wisla.fm.adapter.service.SourceConfigService;
import com.wisla.fm.adapter.service.SourceConfigSyncService;
import com.wisla.fm.adapter.web.dto.ProbeRequest;
import com.wisla.fm.adapter.web.dto.ProbeResponse;
import com.wisla.fm.adapter.web.dto.SourceConfigSnapshotDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class InternalController {

    private final AdapterProperties properties;
    private final SourceConfigService sourceConfigService;
    private final ProbeService probeService;
    private final SourceConfigSyncService sourceConfigSyncService;

    public InternalController(
            AdapterProperties properties,
            SourceConfigService sourceConfigService,
            ProbeService probeService,
            SourceConfigSyncService sourceConfigSyncService
    ) {
        this.properties = properties;
        this.sourceConfigService = sourceConfigService;
        this.probeService = probeService;
        this.sourceConfigSyncService = sourceConfigSyncService;
    }

    @GetMapping("/sources/{sourceId}/config")
    public ResponseEntity<SourceConfigSnapshotDto> getSourceConfig(
            @PathVariable UUID sourceId,
            HttpServletRequest request
    ) {
        requireInternalAuth(request);
        var snapshot = sourceConfigService.requireBySourceId(sourceId);
        return ResponseEntity.ok(sourceConfigService.toDto(snapshot));
    }

    @PostMapping("/probe")
    public ResponseEntity<ProbeResponse> executeProbe(
            @RequestBody ProbeRequest request,
            HttpServletRequest httpRequest
    ) {
        requireInternalAuth(httpRequest);
        return ResponseEntity.ok(probeService.execute(request));
    }

    @PostMapping("/config/sync")
    public ResponseEntity<Void> syncConfig(HttpServletRequest httpRequest) {
        requireInternalAuth(httpRequest);
        sourceConfigSyncService.syncFromFmModule();
        return ResponseEntity.accepted().build();
    }

    private void requireInternalAuth(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        String expected = "Bearer " + properties.internalServiceToken();
        if (auth == null || !auth.equals(expected)) {
            throw new AdapterException(
                    "unauthorized",
                    "Invalid or missing internal service token",
                    HttpStatus.UNAUTHORIZED.value()
            );
        }
    }
}
