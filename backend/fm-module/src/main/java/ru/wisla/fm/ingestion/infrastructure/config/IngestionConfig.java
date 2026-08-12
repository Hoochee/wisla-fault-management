package ru.wisla.fm.ingestion.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.wisla.fm.ingestion.application.port.in.IngestEventsUseCase;
import ru.wisla.fm.ingestion.application.port.in.QueryRawEventsUseCase;
import ru.wisla.fm.ingestion.application.port.out.EventSourceStatePort;
import ru.wisla.fm.ingestion.application.port.out.ProcessRawEventBatchPort;
import ru.wisla.fm.ingestion.application.port.out.RawEventStorePort;
import ru.wisla.fm.ingestion.application.service.IngestEventsService;
import ru.wisla.fm.ingestion.application.service.RawEventQueryService;

import java.time.Clock;

/**
 * Spring wiring for the ingestion use cases, which stay free of framework annotations.
 */
@Configuration
public class IngestionConfig {

    @Bean
    public IngestEventsUseCase ingestEventsUseCase(RawEventStorePort rawEventStore,
                                                   EventSourceStatePort eventSourceState,
                                                   ProcessRawEventBatchPort processRawEventBatch) {
        return new IngestEventsService(rawEventStore, eventSourceState, processRawEventBatch, Clock.systemUTC());
    }

    @Bean
    public QueryRawEventsUseCase queryRawEventsUseCase(RawEventStorePort rawEventStore,
                                                       EventSourceStatePort eventSourceState) {
        return new RawEventQueryService(rawEventStore, eventSourceState);
    }
}
