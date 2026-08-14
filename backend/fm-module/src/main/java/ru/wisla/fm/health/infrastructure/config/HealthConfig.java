package ru.wisla.fm.health.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.wisla.fm.health.application.port.in.GetProductHealthHistoryUseCase;
import ru.wisla.fm.health.application.port.in.GetProductHealthUseCase;
import ru.wisla.fm.health.application.port.in.RecalculateProductHealthUseCase;
import ru.wisla.fm.health.application.port.in.UpdateProductComponentsUseCase;
import ru.wisla.fm.health.application.port.out.ActiveSignalsPort;
import ru.wisla.fm.health.application.port.out.HealthHistoryStorePort;
import ru.wisla.fm.health.application.port.out.HealthSnapshotStorePort;
import ru.wisla.fm.health.application.port.out.ProductAggregateWritePort;
import ru.wisla.fm.health.application.port.out.ProductTopologyPort;
import ru.wisla.fm.health.application.service.GetProductHealthHistoryService;
import ru.wisla.fm.health.application.service.GetProductHealthService;
import ru.wisla.fm.health.application.service.RecalculateProductHealthService;
import ru.wisla.fm.health.application.service.UpdateProductComponentsService;
import ru.wisla.fm.health.domain.HealthCalculator;

import java.time.Clock;

@Configuration
@EnableScheduling
public class HealthConfig {

    @Bean
    HealthCalculator healthCalculator() {
        return new HealthCalculator();
    }

    @Bean
    RecalculateProductHealthUseCase recalculateProductHealthUseCase(
            ProductTopologyPort topologyPort,
            ActiveSignalsPort activeSignalsPort,
            HealthSnapshotStorePort snapshotStore,
            HealthHistoryStorePort historyStore,
            ProductAggregateWritePort productWrite,
            HealthCalculator healthCalculator
    ) {
        return new RecalculateProductHealthService(
                topologyPort,
                activeSignalsPort,
                snapshotStore,
                historyStore,
                productWrite,
                healthCalculator,
                Clock.systemUTC()
        );
    }

    @Bean
    GetProductHealthUseCase getProductHealthUseCase(
            ProductTopologyPort topologyPort,
            HealthSnapshotStorePort snapshotStore,
            HealthHistoryStorePort historyStore
    ) {
        return new GetProductHealthService(topologyPort, snapshotStore, historyStore);
    }

    @Bean
    GetProductHealthHistoryUseCase getProductHealthHistoryUseCase(
            ProductTopologyPort topologyPort,
            HealthHistoryStorePort historyStore
    ) {
        return new GetProductHealthHistoryService(topologyPort, historyStore);
    }

    @Bean
    UpdateProductComponentsUseCase updateProductComponentsUseCase(ProductTopologyPort topologyPort) {
        return new UpdateProductComponentsService(topologyPort);
    }
}
