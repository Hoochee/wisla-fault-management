package ru.wisla.fm.processing.adapter.out.notification;

import org.springframework.stereotype.Component;
import ru.wisla.fm.notifications.api.NotifyStubService;
import ru.wisla.fm.processing.application.port.out.NotificationPort;

import java.util.UUID;

@Component
public class NotifyAdapter implements NotificationPort {

    private final NotifyStubService notifyStubService;

    public NotifyAdapter(NotifyStubService notifyStubService) {
        this.notifyStubService = notifyStubService;
    }

    @Override
    public void notify(UUID ruleId, String channel, String emailAddress) {
        notifyStubService.execute(ruleId, channel, emailAddress);
    }
}
