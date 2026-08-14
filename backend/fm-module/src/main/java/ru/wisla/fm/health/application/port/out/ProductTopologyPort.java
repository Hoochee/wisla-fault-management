package ru.wisla.fm.health.application.port.out;

import ru.wisla.fm.health.domain.ComponentDraft;
import ru.wisla.fm.health.domain.ProductTopology;
import ru.wisla.fm.health.domain.SavedComponent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductTopologyPort {

    Optional<ProductTopology> findById(UUID productId);

    List<ProductTopology> findAll();

    List<UUID> findProductIdsByCiId(UUID ciId);

    List<SavedComponent> replaceComponents(UUID productId, List<ComponentDraft> components);

    List<SavedComponent> bindNewCisToCommon(UUID productId, List<UUID> ciIds);

    List<SavedComponent> listComponents(UUID productId);
}
