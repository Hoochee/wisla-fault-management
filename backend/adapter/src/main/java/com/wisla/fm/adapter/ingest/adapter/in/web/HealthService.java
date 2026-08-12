package com.wisla.fm.adapter.ingest.adapter.in.web;

import com.wisla.fm.adapter.ingest.adapter.in.web.dto.HealthResponse;
import com.wisla.fm.adapter.ingest.adapter.out.http.FmModuleClient;
import com.wisla.fm.adapter.ingest.application.port.out.BufferedEventStorePort;
import com.wisla.fm.adapter.ingest.infrastructure.config.AdapterProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class HealthService {

    private final AdapterProperties properties;
    private final DataSource dataSource;
    private final BufferedEventStorePort bufferedEventStore;
    private final FmModuleClient fmModuleClient;
    private final WebClient fmModuleWebClient;

    public HealthService(
            AdapterProperties properties,
            DataSource dataSource,
            BufferedEventStorePort bufferedEventStore,
            FmModuleClient fmModuleClient,
            WebClient fmModuleWebClient
    ) {
        this.properties = properties;
        this.dataSource = dataSource;
        this.bufferedEventStore = bufferedEventStore;
        this.fmModuleClient = fmModuleClient;
        this.fmModuleWebClient = fmModuleWebClient;
    }

    public ResponseEntity<HealthResponse> getHealth() {
        String databaseStatus = checkDatabase();
        String fmModuleStatus = fmModuleClient.getReachabilityStatus(
                fmModuleWebClient,
                properties.fmModuleHealthCheckEnabled()
        );
        long bufferedCount = safeBufferedCount();

        boolean degraded = "down".equals(databaseStatus)
                || "unreachable".equals(fmModuleStatus);
        String status = degraded ? "degraded" : "ok";

        HealthResponse body = new HealthResponse(
                status,
                properties.version(),
                databaseStatus,
                fmModuleStatus,
                bufferedCount
        );

        HttpStatus httpStatus = "down".equals(databaseStatus)
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(body);
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "up" : "down";
        } catch (Exception ex) {
            return "down";
        }
    }

    private long safeBufferedCount() {
        try {
            return bufferedEventStore.count();
        } catch (Exception ex) {
            return 0L;
        }
    }
}
