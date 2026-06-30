package ru.wisla.fm.notifications.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.notifications.domain.RulePushNotificationEntity;
import ru.wisla.fm.notifications.persistence.RulePushNotificationRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PushNotificationService {

    private final RulePushNotificationRepository repository;

    public PushNotificationService(RulePushNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RulePushNotificationEntity create(UUID ruleId, UUID eventId, String title, String message) {
        RulePushNotificationEntity entity = new RulePushNotificationEntity();
        entity.setRuleId(ruleId);
        entity.setEventId(eventId);
        entity.setTitle(title != null && !title.isBlank() ? title : "Событие");
        entity.setMessage(message != null && !message.isBlank() ? message : entity.getTitle());
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public PushNotificationListDto listSince(Instant since) {
        Instant effectiveSince = since != null ? since : Instant.EPOCH;
        List<PushNotificationDto> items = repository.findByCreatedAtAfterOrderByCreatedAtAsc(effectiveSince).stream()
                .map(entity -> new PushNotificationDto(
                        entity.getId(),
                        entity.getRuleId(),
                        entity.getEventId(),
                        entity.getTitle(),
                        entity.getMessage(),
                        entity.getCreatedAt()
                ))
                .toList();
        return new PushNotificationListDto(items);
    }
}
