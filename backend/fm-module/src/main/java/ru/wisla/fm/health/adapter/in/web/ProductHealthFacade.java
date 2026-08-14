package ru.wisla.fm.health.adapter.in.web;

import org.springframework.stereotype.Component;
import ru.wisla.fm.admin.api.ConfigurationItemDto;
import ru.wisla.fm.cmdb.persistence.ConfigurationItemRepository;
import ru.wisla.fm.cmdb.persistence.ProductCiRepository;
import ru.wisla.fm.cmdb.service.CmdbMapper;
import ru.wisla.fm.common.api.NotFoundException;
import ru.wisla.fm.health.api.ComponentHealthDto;
import ru.wisla.fm.health.api.ProductHealthDetailDto;
import ru.wisla.fm.health.api.ProductHealthDto;
import ru.wisla.fm.health.api.ProductHealthHistoryDto;
import ru.wisla.fm.health.api.SankeyDto;
import ru.wisla.fm.health.api.SankeyLinkDto;
import ru.wisla.fm.health.api.SankeyNodeDto;
import ru.wisla.fm.health.application.port.in.GetProductHealthHistoryUseCase;
import ru.wisla.fm.health.application.port.in.GetProductHealthUseCase;
import ru.wisla.fm.health.domain.ComponentHealth;
import ru.wisla.fm.health.domain.HealthHistoryBucket;
import ru.wisla.fm.health.domain.ProductHealthDetail;
import ru.wisla.fm.health.domain.ProductHealthView;
import ru.wisla.fm.health.domain.ProductNotFoundException;
import ru.wisla.fm.health.domain.Sankey;
import ru.wisla.fm.health.domain.SnapshotPayload;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaEntity;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaRepository;
import ru.wisla.fm.processing.api.EventDto;
import ru.wisla.fm.processing.api.EventQueryService;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ProductHealthFacade {

    private static final List<String> SEVERITY_ORDER = List.of(
            "fatal", "critical", "major", "minor", "warning", "normal"
    );

    private final GetProductHealthUseCase getProductHealth;
    private final GetProductHealthHistoryUseCase getProductHealthHistory;
    private final ConfigurationItemRepository configurationItemRepository;
    private final ProductCiRepository productCiRepository;
    private final EventJpaRepository eventRepository;
    private final EventQueryService eventQueryService;
    private final CmdbMapper cmdbMapper;

    public ProductHealthFacade(
            GetProductHealthUseCase getProductHealth,
            GetProductHealthHistoryUseCase getProductHealthHistory,
            ConfigurationItemRepository configurationItemRepository,
            ProductCiRepository productCiRepository,
            EventJpaRepository eventRepository,
            EventQueryService eventQueryService,
            CmdbMapper cmdbMapper
    ) {
        this.getProductHealth = getProductHealth;
        this.getProductHealthHistory = getProductHealthHistory;
        this.configurationItemRepository = configurationItemRepository;
        this.productCiRepository = productCiRepository;
        this.eventRepository = eventRepository;
        this.eventQueryService = eventQueryService;
        this.cmdbMapper = cmdbMapper;
    }

    public List<ProductHealthDto> listProducts(String tenant, String site, String tag) {
        return getProductHealth.list(tenant, site, tag).stream().map(this::toDto).toList();
    }

    public ProductHealthDetailDto getProduct(UUID id) {
        ProductHealthDetail detail;
        try {
            detail = getProductHealth.get(id);
        } catch (ProductNotFoundException ex) {
            throw new NotFoundException("Product not found");
        }
        ProductHealthView view = detail.summary();
        List<UUID> ciIds = view.ciIds();
        List<ConfigurationItemDto> cis = configurationItemRepository.findAllById(ciIds).stream()
                .map(ci -> cmdbMapper.toDto(ci, productCiRepository.findProductIdsByCiId(ci.getId())))
                .toList();
        List<EventJpaEntity> activeEvents = ciIds.isEmpty() ? List.of() : eventRepository.findActiveByCiIds(ciIds);
        List<EventDto> eventDtos = activeEvents.stream().map(eventQueryService::toDto).toList();
        SnapshotPayload payload = detail.payload();
        Sankey sankey = payload != null && payload.sankey() != null
                ? payload.sankey()
                : new Sankey(List.of(), List.of());
        Map<UUID, String> ciFqdn = new HashMap<>();
        for (ConfigurationItemDto ci : cis) {
            if (ci.id() != null && ci.fqdn() != null && !ci.fqdn().isBlank()) {
                ciFqdn.put(ci.id(), ci.fqdn());
            }
        }
        return new ProductHealthDetailDto(
                view.id(),
                view.name(),
                view.tenant(),
                view.site(),
                view.maxSeverity(),
                view.activeEventCount(),
                view.ciIds(),
                view.tags(),
                view.healthPercent(),
                view.damagePercent(),
                toComponentDtos(view.components()),
                toSankeyDto(Sankey.forDisplay(sankey, view.name(), ciFqdn)),
                cis,
                eventDtos,
                severityBreakdown(activeEvents),
                detail.calculatedAt(),
                detail.minHealthToday(),
                detail.maxHealthToday()
        );
    }

    public List<ProductHealthHistoryDto> history(UUID id, Instant from, Instant to, int bucketMinutes) {
        try {
            return getProductHealthHistory.history(id, from, to, bucketMinutes).stream()
                    .map(this::toHistoryDto)
                    .toList();
        } catch (ProductNotFoundException ex) {
            throw new NotFoundException("Product not found");
        }
    }

    private ProductHealthDto toDto(ProductHealthView view) {
        return new ProductHealthDto(
                view.id(),
                view.name(),
                view.tenant(),
                view.site(),
                view.maxSeverity(),
                view.activeEventCount(),
                view.ciIds(),
                view.tags(),
                view.healthPercent(),
                view.damagePercent(),
                toComponentDtos(view.components())
        );
    }

    private static List<ComponentHealthDto> toComponentDtos(List<ComponentHealth> components) {
        if (components == null) {
            return List.of();
        }
        return components.stream()
                .map(c -> new ComponentHealthDto(
                        c.code(),
                        c.name(),
                        c.healthPercent(),
                        c.damagePercent(),
                        c.weight(),
                        c.influenceType(),
                        c.ciIds()
                ))
                .toList();
    }

    private static SankeyDto toSankeyDto(Sankey sankey) {
        return new SankeyDto(
                sankey.nodes().stream().map(n -> new SankeyNodeDto(n.id(), n.label(), n.kind())).toList(),
                sankey.links().stream().map(l -> new SankeyLinkDto(l.from(), l.to(), l.damage())).toList()
        );
    }

    private ProductHealthHistoryDto toHistoryDto(HealthHistoryBucket bucket) {
        return new ProductHealthHistoryDto(
                bucket.bucketStart(),
                bucket.bucketMinutes(),
                bucket.minHealth(),
                bucket.maxHealth(),
                bucket.worstSeverity()
        );
    }

    private Map<String, Integer> severityBreakdown(List<EventJpaEntity> events) {
        Map<String, Integer> counts = new HashMap<>();
        for (String severity : SEVERITY_ORDER) {
            counts.put(severity, 0);
        }
        for (EventJpaEntity event : events) {
            counts.merge(event.getSeverity(), 1, Integer::sum);
        }
        return counts;
    }
}
