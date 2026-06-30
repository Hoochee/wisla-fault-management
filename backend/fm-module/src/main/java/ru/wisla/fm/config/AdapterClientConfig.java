package ru.wisla.fm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import ru.wisla.fm.adapters.client.AdapterHealthClient;
import ru.wisla.fm.adapters.client.AdapterInternalClient;
import ru.wisla.fm.adapters.client.RestAdapterHealthClient;
import ru.wisla.fm.adapters.client.RestAdapterInternalClient;
import ru.wisla.fm.adapters.client.RestZabbixSimulatorClient;
import ru.wisla.fm.adapters.client.ZabbixSimulatorClient;

@Configuration
public class AdapterClientConfig {

    @Bean
    RestClient adapterRestClient(RestClient.Builder builder, AdapterProperties adapterProperties) {
        return builder.baseUrl(adapterProperties.baseUrl()).build();
    }

    @Bean
    AdapterHealthClient adapterHealthClient(RestClient adapterRestClient) {
        return new RestAdapterHealthClient(adapterRestClient);
    }

    @Bean
    AdapterInternalClient adapterInternalClient(
            RestClient adapterRestClient,
            AdapterProperties adapterProperties
    ) {
        return new RestAdapterInternalClient(adapterRestClient, adapterProperties);
    }

    @Bean
    ZabbixSimulatorClient zabbixSimulatorClient(ZabbixSimulatorProperties properties) {
        RestClient client = RestClient.create(properties.baseUrl());
        return new RestZabbixSimulatorClient(client);
    }
}
