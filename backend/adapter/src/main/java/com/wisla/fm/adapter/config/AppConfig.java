package com.wisla.fm.adapter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    ObjectMapper objectMapper() {
        // Register only JavaTimeModule. findAndRegisterModules() can pick up Jackson's Scala
        // module from spring-kafka-test / Kafka on the classpath and break java.util.Map JSON.
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
