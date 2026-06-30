package com.wisla.fm.adapter.web;

import com.wisla.fm.adapter.persistence.repository.SourceConfigSnapshotRepository;
import com.wisla.fm.adapter.service.FmModuleClient;
import com.wisla.fm.adapter.testsupport.FmModuleClientTestConfiguration;
import com.wisla.fm.adapter.testsupport.SourceConfigTestData;
import com.wisla.fm.adapter.testsupport.TestFmModuleClient;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FmModuleClientTestConfiguration.class)
class InternalControllerTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final String SOURCE_KEY = "probe-source-01";
    private static final String API_KEY = "probe-api-key";
    private static final UUID SOURCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SourceConfigSnapshotRepository sourceConfigRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestFmModuleClient fmModuleClient;

    @BeforeEach
    void setUp() {
        fmModuleClient.resetForwardIngest();
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
    void getSourceConfigReturnsSnapshot() throws Exception {
        mockMvc.perform(get("/internal/sources/{sourceId}/config", SOURCE_ID)
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source_id").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.source_key").value(SOURCE_KEY))
                .andExpect(jsonPath("$.endpoint").value("http://fm-module:8080"))
                .andExpect(jsonPath("$.blocked").value(false))
                .andExpect(jsonPath("$.ttl_expires_at").exists());
    }

    @Test
    void getSourceConfigRequiresInternalAuth() throws Exception {
        mockMvc.perform(get("/internal/sources/{sourceId}/config", SOURCE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void getSourceConfigReturnsNotFoundForMissingSnapshot() throws Exception {
        UUID missingId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        mockMvc.perform(get("/internal/sources/{sourceId}/config", missingId)
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("config_not_found"));
    }

    @Test
    void getSourceConfigReturnsNotFoundForExpiredSnapshot() throws Exception {
        sourceConfigRepository.deleteAll();
        Instant expired = Instant.now().minusSeconds(60);
        sourceConfigRepository.save(SourceConfigTestData.snapshot(
                SOURCE_ID,
                SOURCE_KEY,
                passwordEncoder.encode(API_KEY),
                "http://fm-module:8080",
                Map.of(),
                false,
                expired,
                expired,
                expired
        ));

        mockMvc.perform(get("/internal/sources/{sourceId}/config", SOURCE_ID)
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("config_not_found"));
    }

    @Test
    void executeProbeForwardsSuccessfully() throws Exception {
        fmModuleClient.stubForwardIngest(new FmModuleClient.IngestResult(true, 202, 15L, null, false));

        mockMvc.perform(post("/internal/probe")
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source_id": "%s",
                                  "ingest_api_key": "%s"
                                }
                                """.formatted(SOURCE_ID, API_KEY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.source_id").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.delivery").value("forwarded"))
                .andExpect(jsonPath("$.ingest_status").value(202))
                .andExpect(jsonPath("$.probed_at").exists())
                .andExpect(jsonPath("$.latency_ms").exists());
    }

    @Test
    void executeProbeReportsBufferedDeliveryAsFailure() throws Exception {
        fmModuleClient.stubForwardIngest(new FmModuleClient.IngestResult(false, null, 8L, "timeout", true));

        mockMvc.perform(post("/internal/probe")
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source_id": "%s",
                                  "ingest_api_key": "%s"
                                }
                                """.formatted(SOURCE_ID, API_KEY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.delivery").value("buffered"))
                .andExpect(jsonPath("$.error").value("fm-module unavailable, message buffered"));
    }

    @Test
    void executeProbeRequiresInternalAuth() throws Exception {
        mockMvc.perform(post("/internal/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_id":"%s","ingest_api_key":"%s"}
                                """.formatted(SOURCE_ID, API_KEY)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void executeProbeReturnsNotFoundForUnknownSource() throws Exception {
        UUID missingId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        mockMvc.perform(post("/internal/probe")
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_id":"%s","ingest_api_key":"%s"}
                                """.formatted(missingId, API_KEY)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("config_not_found"));
    }

    @Test
    void executeProbeRejectsBlockedSource() throws Exception {
        sourceConfigRepository.deleteAll();
        sourceConfigRepository.save(SourceConfigTestData.snapshot(
                SOURCE_ID,
                SOURCE_KEY,
                passwordEncoder.encode(API_KEY),
                "http://fm-module:8080",
                Map.of(),
                true
        ));

        mockMvc.perform(post("/internal/probe")
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_id":"%s","ingest_api_key":"%s"}
                                """.formatted(SOURCE_ID, API_KEY)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("source_blocked"));
    }

    @Test
    void executeProbeFailsWithoutIngestApiKey() throws Exception {
        mockMvc.perform(post("/internal/probe")
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source_id":"%s"}
                                """.formatted(SOURCE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.delivery").value("failed"))
                .andExpect(jsonPath("$.error").value("ingest_api_key is required for fm-module delivery test"));
    }

    @Test
    void syncConfigReturnsAccepted() throws Exception {
        mockMvc.perform(post("/internal/config/sync")
                        .header("Authorization", "Bearer " + INTERNAL_TOKEN))
                .andExpect(status().isAccepted());
    }

    @Test
    void syncConfigRequiresInternalAuth() throws Exception {
        mockMvc.perform(post("/internal/config/sync"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }
}
