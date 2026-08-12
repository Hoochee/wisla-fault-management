package com.wisla.fm.adapter.ingest.adapter.in.web;

import com.wisla.fm.adapter.ingest.adapter.in.web.dto.WebhookAcceptedResponse;
import com.wisla.fm.adapter.ingest.application.port.in.ReceiveWebhookCommand;
import com.wisla.fm.adapter.ingest.application.port.in.ReceiveWebhookEventUseCase;
import com.wisla.fm.adapter.ingest.domain.DeliveryOutcome;
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

    private final ReceiveWebhookEventUseCase receiveWebhookEvent;
    private final WebhookPayloadReader payloadReader;

    public WebhookController(
            ReceiveWebhookEventUseCase receiveWebhookEvent,
            WebhookPayloadReader payloadReader
    ) {
        this.receiveWebhookEvent = receiveWebhookEvent;
        this.payloadReader = payloadReader;
    }

    @PostMapping("/webhook/{sourceKey}")
    public ResponseEntity<WebhookAcceptedResponse> receiveWebhook(
            @PathVariable String sourceKey,
            @RequestParam(name = "sourceKey", required = false) String querySourceKey,
            HttpServletRequest request
    ) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String headerApiKey = request.getHeader("X-Source-Key");
        DeliveryOutcome outcome = receiveWebhookEvent.receive(new ReceiveWebhookCommand(
                sourceKey,
                headerApiKey,
                querySourceKey,
                payloadReader.read(body)
        ));
        WebhookAcceptedResponse response =
                new WebhookAcceptedResponse(true, outcome.delivery(), outcome.messageId(), null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
