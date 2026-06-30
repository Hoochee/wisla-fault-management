package ru.wisla.fm.common.api;

import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Optional;

@Component
public class HealthService {

    private final DataSource dataSource;
    private final Optional<BuildProperties> buildProperties;

    public HealthService(DataSource dataSource, Optional<BuildProperties> buildProperties) {
        this.dataSource = dataSource;
        this.buildProperties = buildProperties;
    }

    public HealthStatus getHealth() {
        String database = checkDatabase() ? "up" : "down";
        String status = "up".equals(database) ? "ok" : "degraded";
        String version = buildProperties.map(BuildProperties::getVersion).orElse("0.1.0-SNAPSHOT");
        return new HealthStatus(status, version, database);
    }

    private boolean checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    public record HealthStatus(String status, String version, String database) {
    }
}
