package ru.wisla.fm.notifications.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.notifications.persistence.RulePushNotificationRepository;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PushNotificationServiceTest extends AbstractFmModuleTest {

    @Autowired private PushNotificationService pushNotificationService;
    @Autowired private RulePushNotificationRepository repository;

    @Test
    @Transactional
    void createPersistsOutboxRow() {
        UUID ruleId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        var saved = pushNotificationService.create(ruleId, eventId, "Disk full", "Critical: Disk full");

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findById(saved.getId()))
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.getRuleId()).isEqualTo(ruleId);
                    assertThat(row.getEventId()).isEqualTo(eventId);
                    assertThat(row.getTitle()).isEqualTo("Disk full");
                    assertThat(row.getMessage()).isEqualTo("Critical: Disk full");
                });
    }
}
