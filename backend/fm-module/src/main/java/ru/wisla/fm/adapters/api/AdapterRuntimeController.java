package ru.wisla.fm.adapters.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/adapters")
public class AdapterRuntimeController {

    private final AdapterRuntimeService adapterRuntimeService;

    public AdapterRuntimeController(AdapterRuntimeService adapterRuntimeService) {
        this.adapterRuntimeService = adapterRuntimeService;
    }

    @GetMapping("/runtime")
    public AdapterRuntimeResponse getRuntime() {
        return adapterRuntimeService.getRuntime();
    }
}
