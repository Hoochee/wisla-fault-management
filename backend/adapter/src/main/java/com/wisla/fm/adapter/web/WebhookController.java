package com.wisla.fm.adapter.web;

import com.wisla.fm.adapter.service.WebhookService;
import com.wisla.fm.adapter.web.dto.WebhookAcceptedResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook/{sourceKey}")
    public ResponseEntity<WebhookAcceptedResponse> receiveWebhook(
            @PathVariable String sourceKey,
            @RequestParam(name = "sourceKey", required = false) String querySourceKey,
            HttpServletRequest request
    ) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String headerApiKey = request.getHeader("X-Source-Key");
        WebhookAcceptedResponse response = webhookService.receive(
                sourceKey,
                headerApiKey,
                querySourceKey,
                body
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
