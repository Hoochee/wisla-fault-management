package com.wisla.fm.adapter.ingest.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient fmModuleWebClient(AdapterProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.fmModuleBaseUrl())
                .build();
    }
}
