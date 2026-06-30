package ru.wisla.fm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zabbix-simulator")
public record ZabbixSimulatorProperties(String baseUrl) {
}
