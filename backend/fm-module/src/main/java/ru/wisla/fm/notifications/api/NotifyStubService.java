package ru.wisla.fm.notifications.api;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotifyStubService {

    public void execute(UUID ruleId, String channel, String emailAddress) {
        // MVP stub: no SMTP/Telegram delivery; last_run_at is updated by the caller.
    }
}
