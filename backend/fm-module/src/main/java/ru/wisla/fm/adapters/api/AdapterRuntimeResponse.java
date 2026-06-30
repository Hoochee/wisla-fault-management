package ru.wisla.fm.adapters.api;

import java.util.List;

public record AdapterRuntimeResponse(
        AdapterServiceRuntimeDto service,
        List<SourceAdapterRuntimeDto> sources
) {
}
