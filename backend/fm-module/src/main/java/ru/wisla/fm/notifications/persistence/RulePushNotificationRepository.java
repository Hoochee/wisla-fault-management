package ru.wisla.fm.notifications.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.wisla.fm.notifications.domain.RulePushNotificationEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RulePushNotificationRepository extends JpaRepository<RulePushNotificationEntity, UUID> {

    List<RulePushNotificationEntity> findByCreatedAtAfterOrderByCreatedAtAsc(Instant since);
}
