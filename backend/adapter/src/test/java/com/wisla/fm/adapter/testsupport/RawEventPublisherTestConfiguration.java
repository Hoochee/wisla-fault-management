package com.wisla.fm.adapter.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class RawEventPublisherTestConfiguration {

    @Bean
    @Primary
    TestRawEventPublisher rawEventPublisher() {
        return new TestRawEventPublisher();
    }
}
