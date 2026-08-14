package ru.wisla.fm.health.application.port.in;

import ru.wisla.fm.health.domain.ComponentDraft;
import ru.wisla.fm.health.domain.SavedComponent;

import java.util.List;
import java.util.UUID;

public interface UpdateProductComponentsUseCase {

    List<SavedComponent> update(UUID productId, List<ComponentDraft> components);

    List<SavedComponent> bindNewCisToCommon(UUID productId, List<UUID> ciIds);

    List<SavedComponent> list(UUID productId);
}
