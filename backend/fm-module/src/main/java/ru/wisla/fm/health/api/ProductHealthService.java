package ru.wisla.fm.health.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.wisla.fm.admin.api.ConfigurationItemDto;
import ru.wisla.fm.cmdb.service.CmdbMapper;
import ru.wisla.fm.cmdb.domain.ProductEntity;
import ru.wisla.fm.cmdb.persistence.ConfigurationItemRepository;
import ru.wisla.fm.cmdb.persistence.ProductCiRepository;
import ru.wisla.fm.cmdb.persistence.ProductRepository;
import ru.wisla.fm.common.api.NotFoundException;
import ru.wisla.fm.processing.api.EventDto;
import ru.wisla.fm.processing.api.EventQueryService;
import ru.wisla.fm.processing.domain.EventEntity;
import ru.wisla.fm.processing.persistence.EventRepository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductHealthService {

    private static final List<String> SEVERITY_ORDER = List.of(
            "fatal", "critical", "major", "minor", "warning", "normal"
    );

    private final ProductRepository productRepository;
    private final ProductCiRepository productCiRepository;
    private final ConfigurationItemRepository configurationItemRepository;
    private final EventRepository eventRepository;
    private final EventQueryService eventQueryService;
    private final CmdbMapper cmdbMapper;
    private final ObjectMapper objectMapper;

    public ProductHealthService(ProductRepository productRepository,
                                ProductCiRepository productCiRepository,
                                ConfigurationItemRepository configurationItemRepository,
                                EventRepository eventRepository,
                                EventQueryService eventQueryService,
                                CmdbMapper cmdbMapper,
                                ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.productCiRepository = productCiRepository;
        this.configurationItemRepository = configurationItemRepository;
        this.eventRepository = eventRepository;
        this.eventQueryService = eventQueryService;
        this.cmdbMapper = cmdbMapper;
        this.objectMapper = objectMapper;
    }

    public List<ProductHealthDto> listProducts(String tenant, String site, String tag) {
        return productRepository.findAll().stream()
                .filter(product -> tenant == null || tenant.isBlank() || tenant.equals(product.getTenant()))
                .filter(product -> site == null || site.isBlank() || site.equals(product.getSite()))
                .filter(product -> tag == null || tag.isBlank() || parseTags(product.getTags()).contains(tag))
                .map(this::toProductHealth)
                .toList();
    }

    public ProductHealthDetailDto getProduct(UUID id) {
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        ProductHealthDto health = toProductHealth(product);
        List<UUID> ciIds = health.ciIds();
        List<ConfigurationItemDto> cis = configurationItemRepository.findAllById(ciIds).stream()
                .map(ci -> cmdbMapper.toDto(ci, productCiRepository.findProductIdsByCiId(ci.getId())))
                .toList();
        List<EventEntity> activeEvents = ciIds.isEmpty() ? List.of() : eventRepository.findActiveByCiIds(ciIds);
        List<EventDto> eventDtos = activeEvents.stream().map(eventQueryService::toDto).toList();
        Map<String, Integer> breakdown = severityBreakdown(activeEvents);
        return ProductHealthDetailDto.from(health, cis, eventDtos, breakdown);
    }

    private ProductHealthDto toProductHealth(ProductEntity product) {
        List<UUID> ciIds = productCiRepository.findCiIdsByProductId(product.getId());
        List<EventEntity> activeEvents = ciIds.isEmpty() ? List.of() : eventRepository.findActiveByCiIds(ciIds);
        String maxSeverity = activeEvents.isEmpty() ? "normal" : maxSeverity(activeEvents);
        return new ProductHealthDto(
                product.getId(),
                product.getName(),
                product.getTenant(),
                product.getSite(),
                maxSeverity,
                activeEvents.size(),
                ciIds,
                parseTags(product.getTags())
        );
    }

    private String maxSeverity(List<EventEntity> events) {
        return events.stream()
                .map(EventEntity::getSeverity)
                .min(Comparator.comparingInt(SEVERITY_ORDER::indexOf))
                .orElse("normal");
    }

    private Map<String, Integer> severityBreakdown(List<EventEntity> events) {
        Map<String, Integer> counts = new HashMap<>();
        for (String severity : SEVERITY_ORDER) {
            counts.put(severity, 0);
        }
        for (EventEntity event : events) {
            counts.merge(event.getSeverity(), 1, Integer::sum);
        }
        return counts;
    }

    private List<String> parseTags(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
