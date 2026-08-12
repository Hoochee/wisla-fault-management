package com.wisla.fm.adapter.ingest.application.port.in;

import com.wisla.fm.adapter.ingest.domain.DeliveryOutcome;

public interface DeliverIngestEventUseCase {

    DeliveryOutcome deliver(DeliverCommand command);
}
