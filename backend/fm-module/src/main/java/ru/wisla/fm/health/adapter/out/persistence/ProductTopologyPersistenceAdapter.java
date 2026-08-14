package ru.wisla.fm.health.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.cmdb.domain.ProductEntity;
import ru.wisla.fm.cmdb.persistence.ProductCiRepository;
import ru.wisla.fm.cmdb.persistence.ProductRepository;
import ru.wisla.fm.health.application.port.out.ProductTopologyPort;
import ru.wisla.fm.health.domain.CiMembership;
import ru.wisla.fm.health.domain.ComponentDraft;
import ru.wisla.fm.health.domain.ComponentNode;
import ru.wisla.fm.health.domain.InfluenceType;
import ru.wisla.fm.health.domain.ProductTopology;
import ru.wisla.fm.health.domain.SavedComponent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ProductTopologyPersistenceAdapter implements ProductTopologyPort {

    private final ProductRepository productRepository;
    private final ProductCiRepository productCiRepository;
    private final ProductComponentJpaRepository componentRepository;
    private final ProductComponentCiJpaRepository componentCiRepository;
    private final ObjectMapper objectMapper;

    public ProductTopologyPersistenceAdapter(
            ProductRepository productRepository,
            ProductCiRepository productCiRepository,
            ProductComponentJpaRepository componentRepository,
            ProductComponentCiJpaRepository componentCiRepository,
            ObjectMapper objectMapper
    ) {
        this.productRepository = productRepository;
        this.productCiRepository = productCiRepository;
        this.componentRepository = componentRepository;
        this.componentCiRepository = componentCiRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ProductTopology> findById(UUID productId) {
        return productRepository.findById(productId).map(this::toTopology);
    }

    @Override
    public List<ProductTopology> findAll() {
        return productRepository.findAll().stream().map(this::toTopology).toList();
    }

    @Override
    public List<UUID> findProductIdsByCiId(UUID ciId) {
        Set<UUID> ids = new HashSet<>(productCiRepository.findProductIdsByCiId(ciId));
        for (ProductComponentCiJpaEntity link : componentCiRepository.findByIdCiId(ciId)) {
            componentRepository.findById(link.getId().getComponentId())
                    .ifPresent(component -> ids.add(component.getProductId()));
        }
        return List.copyOf(ids);
    }

    @Override
    @Transactional
    public List<SavedComponent> replaceComponents(UUID productId, List<ComponentDraft> components) {
        List<ProductComponentJpaEntity> existing = componentRepository.findByProductIdOrderBySortOrderAsc(productId);
        if (!existing.isEmpty()) {
            componentCiRepository.deleteByIdComponentIdIn(existing.stream().map(ProductComponentJpaEntity::getId).toList());
            componentRepository.deleteByProductId(productId);
            componentRepository.flush();
        }
        int order = 0;
        for (ComponentDraft draft : components) {
            ProductComponentJpaEntity entity = new ProductComponentJpaEntity();
            entity.setProductId(productId);
            entity.setCode(draft.code());
            entity.setName(draft.name() == null || draft.name().isBlank() ? draft.code() : draft.name());
            entity.setWeight(draft.weight());
            entity.setInfluenceType(InfluenceType.fromWire(draft.influenceType()).toWire());
            entity.setCriticalThreshold(draft.criticalThreshold() == null ? 100 : draft.criticalThreshold());
            entity.setSortOrder(draft.sortOrder() > 0 ? draft.sortOrder() : order);
            ProductComponentJpaEntity saved = componentRepository.save(entity);
            if (draft.ciIds() != null) {
                for (UUID ciId : draft.ciIds()) {
                    ProductComponentCiJpaEntity link = new ProductComponentCiJpaEntity();
                    link.setId(new ProductComponentCiId(saved.getId(), ciId));
                    componentCiRepository.save(link);
                }
            }
            order++;
        }
        return listComponents(productId);
    }

    @Override
    @Transactional
    public List<SavedComponent> bindNewCisToCommon(UUID productId, List<UUID> ciIds) {
        ProductComponentJpaEntity common = componentRepository.findByProductIdAndCode(productId, "COMMON")
                .orElseGet(() -> {
                    ProductComponentJpaEntity created = new ProductComponentJpaEntity();
                    created.setProductId(productId);
                    created.setCode("COMMON");
                    created.setName("COMMON");
                    created.setWeight(100);
                    created.setInfluenceType(InfluenceType.WEIGHTED.toWire());
                    created.setCriticalThreshold(100);
                    created.setSortOrder(0);
                    return componentRepository.save(created);
                });
        Set<UUID> slotted = slottedCiIds(productId);
        for (UUID ciId : ciIds) {
            if (slotted.contains(ciId)) {
                continue;
            }
            ProductComponentCiJpaEntity link = new ProductComponentCiJpaEntity();
            link.setId(new ProductComponentCiId(common.getId(), ciId));
            componentCiRepository.save(link);
            slotted.add(ciId);
        }
        return listComponents(productId);
    }

    @Override
    public List<SavedComponent> listComponents(UUID productId) {
        List<ProductComponentJpaEntity> components = componentRepository.findByProductIdOrderBySortOrderAsc(productId);
        Map<UUID, List<ProductComponentCiJpaEntity>> links = linksByComponent(components);
        List<SavedComponent> result = new ArrayList<>();
        for (ProductComponentJpaEntity component : components) {
            List<UUID> ciIds = links.getOrDefault(component.getId(), List.of()).stream()
                    .map(link -> link.getId().getCiId())
                    .toList();
            result.add(new SavedComponent(
                    component.getId(),
                    component.getCode(),
                    component.getName(),
                    component.getWeight(),
                    component.getInfluenceType(),
                    component.getCriticalThreshold(),
                    component.getSortOrder(),
                    ciIds
            ));
        }
        return result;
    }

    private ProductTopology toTopology(ProductEntity product) {
        List<UUID> ciIds = productCiRepository.findCiIdsByProductId(product.getId());
        List<ProductComponentJpaEntity> components = componentRepository.findByProductIdOrderBySortOrderAsc(product.getId());
        Map<UUID, List<ProductComponentCiJpaEntity>> links = linksByComponent(components);
        List<ComponentNode> nodes = new ArrayList<>();
        for (ProductComponentJpaEntity component : components) {
            List<CiMembership> memberships = links.getOrDefault(component.getId(), List.of()).stream()
                    .map(link -> new CiMembership(link.getId().getCiId(), link.getWeight()))
                    .toList();
            nodes.add(new ComponentNode(
                    component.getId(),
                    component.getCode(),
                    component.getName(),
                    component.getWeight(),
                    InfluenceType.fromWire(component.getInfluenceType()),
                    component.getCriticalThreshold(),
                    component.getSortOrder(),
                    memberships
            ));
        }
        return new ProductTopology(
                product.getId(),
                product.getName(),
                product.getTenant(),
                product.getSite(),
                parseTags(product.getTags()),
                ciIds,
                nodes
        );
    }

    private Map<UUID, List<ProductComponentCiJpaEntity>> linksByComponent(List<ProductComponentJpaEntity> components) {
        if (components.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = components.stream().map(ProductComponentJpaEntity::getId).toList();
        return componentCiRepository.findByIdComponentIdIn(ids).stream()
                .collect(Collectors.groupingBy(link -> link.getId().getComponentId()));
    }

    private Set<UUID> slottedCiIds(UUID productId) {
        List<ProductComponentJpaEntity> components = componentRepository.findByProductIdOrderBySortOrderAsc(productId);
        Set<UUID> slotted = new HashSet<>();
        for (List<ProductComponentCiJpaEntity> group : linksByComponent(components).values()) {
            for (ProductComponentCiJpaEntity link : group) {
                slotted.add(link.getId().getCiId());
            }
        }
        return slotted;
    }

    private List<String> parseTags(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
