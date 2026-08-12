package com.wisla.fm.adapter.ingest.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookAcceptedResponse(
        boolean accepted,
        String delivery,
        UUID message_id,
        Integer ingest_status
) {
}
