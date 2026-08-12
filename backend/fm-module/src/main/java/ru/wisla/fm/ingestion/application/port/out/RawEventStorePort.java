package ru.wisla.fm.ingestion.application.port.out;

import ru.wisla.fm.ingestion.domain.RawEvent;
import ru.wisla.fm.ingestion.domain.RawEventBatch;

import java.util.UUID;

public interface RawEventStorePort {

    /**
     * @return the identifier assigned to the stored raw event
     * @throws RuntimeException when the raw event cannot be serialized or stored; the caller counts
     *         such a failure as rejected
     */
    UUID save(RawEvent rawEvent);

    RawEventBatch findPage(UUID sourceId, String severity, Boolean processed, int page, int size);
}
