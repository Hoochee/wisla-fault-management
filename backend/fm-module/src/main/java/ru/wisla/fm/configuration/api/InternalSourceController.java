package ru.wisla.fm.configuration.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/sources")
public class InternalSourceController {

    private final SourceService sourceService;

    public InternalSourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @GetMapping("/{id}/config")
    public InternalSourceConfigDto getSourceConfig(@PathVariable UUID id) {
        return sourceService.getInternalConfig(id);
    }

    @GetMapping
    public List<InternalSourceIndexDto> listSourcesForAdapter() {
        return sourceService.listInternalSources();
    }
}
