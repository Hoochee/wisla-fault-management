package com.wisla.fm.adapter.testsupport;

import com.wisla.fm.adapter.service.FmModuleClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FmModuleClientTestConfiguration {

    @Bean
    @Primary
    TestFmModuleClient fmModuleClient() {
        return new TestFmModuleClient();
    }
}
