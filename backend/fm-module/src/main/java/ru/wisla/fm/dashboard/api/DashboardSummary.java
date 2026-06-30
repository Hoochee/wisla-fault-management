package ru.wisla.fm.dashboard.api;

import java.util.List;
import java.util.Map;

public record DashboardSummary(
        Map<String, Integer> severityCounts,
        int totalActive,
        List<ProductHealthPreview> productPreview,
        List<SystemMapPreview> systemMaps
) {
}
