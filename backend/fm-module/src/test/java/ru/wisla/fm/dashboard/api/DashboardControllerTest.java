package ru.wisla.fm.dashboard.api;

import org.junit.jupiter.api.Test;
import ru.wisla.fm.support.AbstractFmModuleTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest extends AbstractFmModuleTest {

  @Test
  void summaryWithoutAuthReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());
  }

  @Test
  void summaryReturnsAggregates() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/dashboard/summary").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.severityCounts").isMap())
        .andExpect(jsonPath("$.severityCounts.fatal").exists())
        .andExpect(jsonPath("$.totalActive").isNumber())
        .andExpect(jsonPath("$.productPreview").isArray())
        .andExpect(jsonPath("$.systemMaps").isArray());
  }
}
