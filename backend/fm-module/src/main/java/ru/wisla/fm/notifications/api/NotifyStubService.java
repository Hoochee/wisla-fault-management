package ru.wisla.fm.notifications.api;

import org.springframework.stereotype.Service;
import ru.wisla.fm.processing.canvas.ProcessingDecision;

@Service
public class NotifyStubService {

    public void execute(ProcessingDecision.NotifyIntent intent) {
        // MVP stub: no SMTP/Telegram delivery; last_run_at is updated by the caller.
    }
}
