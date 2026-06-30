package ru.wisla.fm.configuration.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wisla.fm.configuration.domain.EventSourceEntity;

import java.util.List;
import java.util.UUID;

public interface EventSourceRepository extends JpaRepository<EventSourceEntity, UUID> {

    List<EventSourceEntity> findByStatus(String status);

    java.util.Optional<EventSourceEntity> findByWebhookPathKey(String webhookPathKey);
}
