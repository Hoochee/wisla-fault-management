package ru.wisla.fm.configuration.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @GetMapping
    public List<EventSourceDto> listSources(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search
    ) {
        return sourceService.listSources(status, type, search);
    }

    @PostMapping
    public ResponseEntity<EventSourceDto> createSource(@Valid @RequestBody EventSourceCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceService.createSource(request));
    }

    @GetMapping("/{id}")
    public EventSourceDetailDto getSource(@PathVariable UUID id) {
        return sourceService.getSource(id);
    }

    @PatchMapping("/{id}")
    public EventSourceDto patchSource(@PathVariable UUID id, @RequestBody EventSourcePatch patch) {
        return sourceService.patchSource(id, patch);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSource(@PathVariable UUID id) {
        sourceService.deleteSource(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public SourceTestResult testSource(
            @PathVariable UUID id,
            @RequestBody(required = false) SourceTestRequest request
    ) {
        String ingestApiKey = request != null ? request.ingestApiKey() : null;
        return sourceService.testSource(id, ingestApiKey);
    }

    @PostMapping("/{id}/bind-simulator")
    public BindSimulatorResponse bindSimulator(
            @PathVariable UUID id,
            @Valid @RequestBody BindSimulatorRequest request
    ) {
        return sourceService.bindSimulator(id, request.ingestApiKey());
    }

    @PostMapping("/{id}/send-test-event")
    public SourceSimulatorTickResult sendTestEvent(@PathVariable UUID id) {
        return sourceService.sendTestEvent(id);
    }

    @GetMapping("/{id}/simulator-status")
    public SourceSimulatorStatus getSimulatorStatus(@PathVariable UUID id) {
        return sourceService.getSimulatorStatus(id);
    }

    @PostMapping("/{id}/simulator-control")
    public SourceSimulatorControlResult setSimulatorControl(
            @PathVariable UUID id,
            @Valid @RequestBody SourceSimulatorControlRequest request
    ) {
        return sourceService.setSimulatorControl(id, request.enabled());
    }
}
