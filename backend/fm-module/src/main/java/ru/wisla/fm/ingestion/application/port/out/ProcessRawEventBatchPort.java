package ru.wisla.fm.ingestion.application.port.out;

import java.util.List;
import java.util.UUID;

public interface ProcessRawEventBatchPort {

    void process(List<UUID> rawEventIds);
}
