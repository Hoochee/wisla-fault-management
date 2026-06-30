package ru.wisla.fm.processing.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wisla.fm.processing.domain.EventActionLogEntity;

import java.util.List;
import java.util.UUID;

public interface EventActionLogRepository extends JpaRepository<EventActionLogEntity, UUID> {

    List<EventActionLogEntity> findByEventIdOrderByCreatedAtDesc(UUID eventId);
}
