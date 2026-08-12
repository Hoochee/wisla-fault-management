package com.wisla.fm.adapter.ingest.adapter.in.web;

import com.wisla.fm.adapter.ingest.adapter.in.web.dto.ProbeRequest;
import com.wisla.fm.adapter.ingest.adapter.in.web.dto.ProbeResponse;
import com.wisla.fm.adapter.ingest.adapter.in.web.dto.SourceConfigSnapshotDto;
import com.wisla.fm.adapter.ingest.application.port.in.SyncSourceConfigUseCase;
import com.wisla.fm.adapter.ingest.domain.IngestRejection;
import com.wisla.fm.adapter.ingest.infrastructure.config.AdapterProperties;
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
    private final SourceConfigSnapshotReader sourceConfigSnapshotReader;
    private final ProbeService probeService;
    private final SyncSourceConfigUseCase syncSourceConfig;

    public InternalController(
            AdapterProperties properties,
            SourceConfigSnapshotReader sourceConfigSnapshotReader,
            ProbeService probeService,
            SyncSourceConfigUseCase syncSourceConfig
    ) {
        this.properties = properties;
        this.sourceConfigSnapshotReader = sourceConfigSnapshotReader;
        this.probeService = probeService;
        this.syncSourceConfig = syncSourceConfig;
    }

    @GetMapping("/sources/{sourceId}/config")
    public ResponseEntity<SourceConfigSnapshotDto> getSourceConfig(
            @PathVariable UUID sourceId,
            HttpServletRequest request
    ) {
        requireInternalAuth(request);
        var snapshot = sourceConfigSnapshotReader.requireBySourceId(sourceId);
        return ResponseEntity.ok(sourceConfigSnapshotReader.toDto(snapshot));
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
        syncSourceConfig.sync();
        return ResponseEntity.accepted().build();
    }

    private void requireInternalAuth(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        String expected = "Bearer " + properties.internalServiceToken();
        if (auth == null || !auth.equals(expected)) {
            throw new IngestRejection(
                    "unauthorized",
                    "Invalid or missing internal service token",
                    HttpStatus.UNAUTHORIZED.value()
            );
        }
    }
}
