package ru.wisla.fm.health.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ru.wisla.fm.support.AbstractFmModuleTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductHealthControllerTest extends AbstractFmModuleTest {

  @Test
  void listProductsWithoutAuthReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/health/products")).andExpect(status().isUnauthorized());
  }

  @Test
  void listProductsReturnsSeededProduct() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/health/products").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[0].id").exists())
        .andExpect(jsonPath("$[0].name").exists())
        .andExpect(jsonPath("$[0].maxSeverity").exists())
        .andExpect(jsonPath("$[0].activeEventCount").isNumber())
        .andExpect(jsonPath("$[0].ciIds").isArray())
        .andExpect(jsonPath("$[0].healthPercent").isNumber())
        .andExpect(jsonPath("$[0].damagePercent").isNumber());
  }

  @Test
  void listProductsFiltersByTenant() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(
            get("/api/v1/health/products")
                .param("tenant", "moscow")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[0].tenant").value("moscow"));
  }

  @Test
  void getProductDetailReturnsCisAndEvents() throws Exception {
    String token = obtainAdminToken();
    MvcResult list =
        mockMvc
            .perform(get("/api/v1/health/products").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode products = objectMapper.readTree(list.getResponse().getContentAsString());
    String productId = products.get(0).get("id").asText();

    mockMvc
        .perform(
            get("/api/v1/health/products/" + productId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(productId))
        .andExpect(jsonPath("$.configurationItems").isArray())
        .andExpect(jsonPath("$.activeEvents").isArray())
        .andExpect(jsonPath("$.severityBreakdown").exists())
        .andExpect(jsonPath("$.healthPercent").isNumber())
        .andExpect(jsonPath("$.damagePercent").isNumber())
        .andExpect(jsonPath("$.sankey").exists());
  }

  @Test
  void getUnknownProductReturns404() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(
            get("/api/v1/health/products/00000000-0000-0000-0000-000000000099")
                .header("Authorization", bearer(token)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("not_found"));
  }

  @Test
  void historyWithoutAuthReturns401() throws Exception {
    mockMvc
        .perform(get("/api/v1/health/products/00000000-0000-0000-0000-000000000001/history"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void historyReturnsBuckets() throws Exception {
    String token = obtainAdminToken();
    MvcResult list =
        mockMvc
            .perform(get("/api/v1/health/products").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn();
    String productId =
        objectMapper.readTree(list.getResponse().getContentAsString()).get(0).get("id").asText();

    mockMvc
        .perform(
            get("/api/v1/health/products/" + productId + "/history")
                .param("bucketMinutes", "15")
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }
}
