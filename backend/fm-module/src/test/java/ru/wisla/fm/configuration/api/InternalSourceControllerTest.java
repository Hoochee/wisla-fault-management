package ru.wisla.fm.configuration.api;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.support.AbstractFmModuleTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalSourceControllerTest extends AbstractFmModuleTest {

  @Test
  void listInternalSourcesWithoutServiceKeyReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/internal/sources")).andExpect(status().isUnauthorized());
  }

  @Test
  void listInternalSourcesIncludesTypeScheduleAndParserConfig() throws Exception {
    mockMvc
        .perform(get("/api/v1/internal/sources").header("X-Service-Key", "test-service-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[0].sourceId").exists())
        .andExpect(jsonPath("$[0].sourceKey").exists())
        .andExpect(jsonPath("$[0].type").value("push_rest"))
        .andExpect(jsonPath("$[0].parserConfig").isMap());
  }
}
