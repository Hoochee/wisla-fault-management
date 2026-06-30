package ru.wisla.fm.console.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wisla.fm.console.domain.EventMapEntity;

import java.util.List;
import java.util.UUID;

public interface EventMapRepository extends JpaRepository<EventMapEntity, UUID> {

    List<EventMapEntity> findBySystemTrueOrderBySortOrderAsc();
}
