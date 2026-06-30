package ru.wisla.fm.dashboard.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.wisla.fm.cmdb.domain.ProductEntity;
import ru.wisla.fm.cmdb.persistence.ProductRepository;
import ru.wisla.fm.console.domain.EventMapEntity;
import ru.wisla.fm.console.persistence.EventMapRepository;
import ru.wisla.fm.processing.persistence.EventRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardService {

    private static final List<String> SEVERITIES = List.of("fatal", "critical", "major", "minor", "warning", "normal");

    private final EventRepository eventRepository;
    private final ProductRepository productRepository;
    private final EventMapRepository eventMapRepository;
    private final ObjectMapper objectMapper;

    public DashboardService(EventRepository eventRepository,
                            ProductRepository productRepository,
                            EventMapRepository eventMapRepository,
                            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.productRepository = productRepository;
        this.eventMapRepository = eventMapRepository;
        this.objectMapper = objectMapper;
    }

    public DashboardSummary getSummary() {
        Map<String, Integer> severityCounts = new HashMap<>();
        for (String severity : SEVERITIES) {
            severityCounts.put(severity, 0);
        }
        for (Object[] row : eventRepository.countActiveBySeverity()) {
            severityCounts.put((String) row[0], ((Number) row[1]).intValue());
        }
        int totalActive = severityCounts.values().stream().mapToInt(Integer::intValue).sum();

        List<ProductHealthPreview> productPreview = productRepository.findAll().stream()
                .limit(5)
                .map(this::toProductPreview)
                .toList();

        List<SystemMapPreview> systemMaps = eventMapRepository.findBySystemTrueOrderBySortOrderAsc().stream()
                .map(this::toMapPreview)
                .toList();

        return new DashboardSummary(severityCounts, totalActive, productPreview, systemMaps);
    }

    private ProductHealthPreview toProductPreview(ProductEntity product) {
        return new ProductHealthPreview(
                product.getId(),
                product.getName(),
                product.getTenant(),
                product.getSite(),
                product.getMaxSeverity(),
                product.getActiveEventCount(),
                List.of(),
                List.of()
        );
    }

    private SystemMapPreview toMapPreview(EventMapEntity map) {
        return new SystemMapPreview(
                map.getId(),
                map.getName(),
                map.getQuery(),
                map.isSystem(),
                map.isPersonal(),
                parseColumns(map.getColumns())
        );
    }

    private List<String> parseColumns(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
