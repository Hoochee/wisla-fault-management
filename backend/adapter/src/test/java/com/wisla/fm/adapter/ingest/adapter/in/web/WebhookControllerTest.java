package com.wisla.fm.adapter.ingest.adapter.in.web;

import com.wisla.fm.adapter.ingest.adapter.out.persistence.SourceConfigSnapshotJpaRepository;
import com.wisla.fm.adapter.ingest.application.port.out.RawEventPublisherPort;
import com.wisla.fm.adapter.testsupport.RawEventPublisherTestConfiguration;
import com.wisla.fm.adapter.testsupport.SourceConfigTestData;
import com.wisla.fm.adapter.testsupport.TestRawEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RawEventPublisherTestConfiguration.class)
class WebhookControllerTest {

    private static final String SOURCE_KEY = "zabbix-prod-01";
    private static final String API_KEY = "test-source-api-key";
    private static final UUID SOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SourceConfigSnapshotJpaRepository sourceConfigRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestRawEventPublisher rawEventPublisher;

    @BeforeEach
    void setUp() {
        rawEventPublisher.reset();
        sourceConfigRepository.deleteAll();
        sourceConfigRepository.save(SourceConfigTestData.snapshot(
                SOURCE_ID,
                SOURCE_KEY,
                passwordEncoder.encode(API_KEY),
                "http://fm-module:8080",
                Map.of("enabled", true),
                false
        ));
    }

    @Test
    void receiveWebhookPublishesToKafka() throws Exception {
        rawEventPublisher.stubPublish(RawEventPublisherPort.PublishResult.ok());

        mockMvc.perform(post("/webhook/{sourceKey}", SOURCE_KEY)
                        .header("X-Source-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event_id":"12345","severity":"high","host":"server01"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.delivery").value("forwarded"));

        assertThat(rawEventPublisher.getPublishCount()).isEqualTo(1);
        assertThat(rawEventPublisher.getLastSourceId()).isEqualTo(SOURCE_ID);
        assertThat(rawEventPublisher.getLastSourceKey()).isEqualTo(SOURCE_KEY);
        assertThat(rawEventPublisher.getLastBody()).containsKey("events");
        assertThat(rawEventPublisher.getLastBody()).doesNotContainKey("apiKey");
    }

    @Test
    void receiveWebhookBuffersWhenKafkaUnavailable() throws Exception {
        rawEventPublisher.stubPublish(RawEventPublisherPort.PublishResult.retryable("broker down"));

        mockMvc.perform(post("/webhook/{sourceKey}", SOURCE_KEY)
                        .header("X-Source-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_id\":\"99\",\"severity\":\"low\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.delivery").value("buffered"))
                .andExpect(jsonPath("$.message_id").exists());
    }

    @Test
    void receiveWebhookRejectsMissingApiKey() throws Exception {
        mockMvc.perform(post("/webhook/{sourceKey}", SOURCE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_id\":\"1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing_api_key"));
    }

    @Test
    void receiveWebhookRejectsInvalidApiKey() throws Exception {
        mockMvc.perform(post("/webhook/{sourceKey}", SOURCE_KEY)
                        .header("X-Source-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_id\":\"1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_source_key"));
    }

    @Test
    void receiveWebhookRejectsMismatchedHeaderAndQueryKeys() throws Exception {
        mockMvc.perform(post("/webhook/{sourceKey}", SOURCE_KEY)
                        .param("sourceKey", API_KEY)
                        .header("X-Source-Key", "other-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_id\":\"1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_source_key"));
    }

    @Test
    void receiveWebhookAcceptsApiKeyFromQueryParameter() throws Exception {
        rawEventPublisher.stubPublish(RawEventPublisherPort.PublishResult.ok());

        mockMvc.perform(post("/webhook/{sourceKey}", SOURCE_KEY)
                        .param("sourceKey", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_id\":\"1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.delivery").value("forwarded"));
    }

    @Test
    void receiveWebhookReturnsNotFoundForUnknownSource() throws Exception {
        mockMvc.perform(post("/webhook/{sourceKey}", "unknown-source")
                        .header("X-Source-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_id\":\"1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("unknown_source"));
    }

    @Test
    void receiveWebhookRejectsInvalidJson() throws Exception {
        mockMvc.perform(post("/webhook/{sourceKey}", SOURCE_KEY)
                        .header("X-Source-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_json"));
    }

    @Test
    void receiveWebhookRejectsFilteredPayload() throws Exception {
        sourceConfigRepository.deleteAll();
        sourceConfigRepository.save(SourceConfigTestData.snapshot(
                SOURCE_ID,
                SOURCE_KEY,
                passwordEncoder.encode(API_KEY),
                "http://fm-module:8080",
                Map.of(
                        "enabled", true,
                        "drop_if", List.of(Map.of("field", "severity", "op", "eq", "value", "low"))
                ),
                false
        ));

        mockMvc.perform(post("/webhook/{sourceKey}", SOURCE_KEY)
                        .header("X-Source-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_id\":\"1\",\"severity\":\"low\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("filtered"));

        assertThat(rawEventPublisher.getPublishCount()).isZero();
    }

    @Test
    void receiveWebhookRejectsBlockedSource() throws Exception {
        sourceConfigRepository.deleteAll();
        sourceConfigRepository.save(SourceConfigTestData.snapshot(
                SOURCE_ID,
                SOURCE_KEY,
                passwordEncoder.encode(API_KEY),
                "http://fm-module:8080",
                Map.of(),
                true
        ));

        mockMvc.perform(post("/webhook/{sourceKey}", SOURCE_KEY)
                        .header("X-Source-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"event_id\":\"1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("source_blocked"));
    }

    @Test
    void receiveWebhookRejectsOversizedPayload() throws Exception {
        String oversized = "x".repeat(2000);

        mockMvc.perform(post("/webhook/{sourceKey}", SOURCE_KEY)
                        .header("X-Source-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"" + oversized + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("payload_too_large"));
    }
}
