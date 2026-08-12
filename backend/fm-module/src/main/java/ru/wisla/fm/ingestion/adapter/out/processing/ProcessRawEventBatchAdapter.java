package ru.wisla.fm.ingestion.adapter.out.processing;

import org.springframework.stereotype.Component;
import ru.wisla.fm.ingestion.application.port.out.ProcessRawEventBatchPort;
import ru.wisla.fm.processing.application.port.in.ProcessRawEventBatchUseCase;

import java.util.List;
import java.util.UUID;

/**
 * The only place where {@code ingestion} references the {@code processing} context, and it references
 * nothing but that context's inbound port.
 */
@Component
public class ProcessRawEventBatchAdapter implements ProcessRawEventBatchPort {

    private final ProcessRawEventBatchUseCase processRawEventBatch;

    public ProcessRawEventBatchAdapter(ProcessRawEventBatchUseCase processRawEventBatch) {
        this.processRawEventBatch = processRawEventBatch;
    }

    @Override
    public void process(List<UUID> rawEventIds) {
        processRawEventBatch.process(rawEventIds);
    }
}
