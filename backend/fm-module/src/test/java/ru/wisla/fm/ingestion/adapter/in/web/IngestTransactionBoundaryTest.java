package ru.wisla.fm.ingestion.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.wisla.fm.ingestion.adapter.out.persistence.RawEventJpaEntity;
import ru.wisla.fm.ingestion.adapter.out.persistence.RawEventJpaRepository;
import ru.wisla.fm.processing.adapter.out.cmdb.CiLookupAdapter;
import ru.wisla.fm.processing.application.port.out.CiLookupPort;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for the {@code @Transactional} move onto the inbound adapters (design decision D3):
 * a processing failure inside the ingest transaction must record {@code raw_events.processing_error}
 * and the transaction must still commit the raw events, without aborting the rest of the batch.
 */
class IngestTransactionBoundaryTest extends AbstractFmModuleTest {

  private static final String FAILING_PREFIX = "boom-";
  private static final String FAILING_FQDN = "boom.wisla.local";
  private static final String FAILURE_MESSAGE = "processing exploded for this raw event";

  @Autowired private RawEventJpaRepository rawEventRepository;

  @Test
  void processingFailureRecordsProcessingErrorAndStillCommitsRawEvents() throws Exception {
    long before = rawEventRepository.count();
    String failingExternalId = FAILING_PREFIX + System.nanoTime();
    String healthyExternalId = "ok-" + System.nanoTime();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/ingest")
                    .header("X-Api-Key", DEMO_SOURCE_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "adapterVersion",
                                "txn-boundary-1.0.0",
                                "events",
                                List.of(
                                    event(failingExternalId, FAILING_FQDN),
                                    event(healthyExternalId, "demo-server.wisla.local"))))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.accepted").value(2))
            .andExpect(jsonPath("$.rejected").value(0))
            .andReturn();

    List<UUID> rawEventIds = rawEventIds(result);
    assertThat(rawEventIds).hasSize(2);

    // The ingest transaction committed even though processing of the first event failed.
    assertThat(rawEventRepository.count()).isEqualTo(before + 2);

    RawEventJpaEntity failed = rawEventRepository.findById(rawEventIds.getFirst()).orElseThrow();
    assertThat(failed.getExternalId()).isEqualTo(failingExternalId);
    assertThat(failed.getProcessingError()).isEqualTo(FAILURE_MESSAGE);
    assertThat(failed.isProcessed()).isFalse();
    assertThat(failed.getProcessedEventId()).isNull();

    RawEventJpaEntity healthy = rawEventRepository.findById(rawEventIds.get(1)).orElseThrow();
    assertThat(healthy.getExternalId()).isEqualTo(healthyExternalId);
    assertThat(healthy.getProcessingError()).isNull();
    assertThat(healthy.isProcessed()).isTrue();
    assertThat(healthy.getProcessedEventId()).isNotNull();
  }

  private List<UUID> rawEventIds(MvcResult result) throws Exception {
    JsonNode ids =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("rawEventIds");
    List<UUID> parsed = new ArrayList<>();
    ids.forEach(id -> parsed.add(UUID.fromString(id.asText())));
    return parsed;
  }

  private static Map<String, Object> event(String externalId, String nodeFqdn) {
    return Map.of(
        "externalId",
        externalId,
        "title",
        "Transaction boundary probe",
        "severity",
        "major",
        "occurredAt",
        Instant.now().toString(),
        "nodeFqdn",
        nodeFqdn);
  }

  /**
   * Fails processing for a single raw event on the CI lookup, the first step inside the production
   * try/catch. The failure is raised before the delegate — and therefore before any
   * {@code @Transactional} collaborator — is entered, so it stays a plain in-memory exception and
   * does not mark the surrounding ingest transaction rollback-only, exactly like the errors the
   * production catch block handles.
   */
  @TestConfiguration
  static class FailingCiLookupConfig {

    @Bean
    @Primary
    CiLookupPort failingCiLookup(CiLookupAdapter delegate) {
      return fqdn -> {
        if (FAILING_FQDN.equals(fqdn)) {
          throw new IllegalStateException(FAILURE_MESSAGE);
        }
        return delegate.findOrCreateByFqdn(fqdn);
      };
    }
  }
}
