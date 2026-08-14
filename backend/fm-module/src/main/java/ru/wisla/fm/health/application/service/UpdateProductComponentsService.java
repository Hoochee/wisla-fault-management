package ru.wisla.fm.health.application.service;

import ru.wisla.fm.health.application.port.in.UpdateProductComponentsUseCase;
import ru.wisla.fm.health.application.port.out.ProductTopologyPort;
import ru.wisla.fm.health.domain.ComponentDraft;
import ru.wisla.fm.health.domain.SavedComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class UpdateProductComponentsService implements UpdateProductComponentsUseCase {

    private final ProductTopologyPort topologyPort;

    public UpdateProductComponentsService(ProductTopologyPort topologyPort) {
        this.topologyPort = topologyPort;
    }

    @Override
    public List<SavedComponent> update(UUID productId, List<ComponentDraft> components) {
        if (components == null) {
            return topologyPort.listComponents(productId);
        }
        validate(components);
        return topologyPort.replaceComponents(productId, components);
    }

    @Override
    public List<SavedComponent> bindNewCisToCommon(UUID productId, List<UUID> ciIds) {
        return topologyPort.bindNewCisToCommon(productId, ciIds == null ? List.of() : ciIds);
    }

    @Override
    public List<SavedComponent> list(UUID productId) {
        return topologyPort.listComponents(productId);
    }

    private static void validate(List<ComponentDraft> components) {
        boolean anyWeight = components.stream().anyMatch(c -> c.weight() > 0);
        if (!anyWeight) {
            throw new IllegalArgumentException("At least one component weight must be greater than 0");
        }
        Set<UUID> seen = new HashSet<>();
        for (ComponentDraft component : components) {
            if (component.ciIds() == null) {
                continue;
            }
            for (UUID ciId : component.ciIds()) {
                if (!seen.add(ciId)) {
                    throw new IllegalArgumentException("CI cannot belong to two components of the same product");
                }
            }
        }
    }
}
