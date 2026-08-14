package ru.wisla.fm.admin.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductAdminControllerTest extends AbstractFmModuleTest {

  @Test
  void createGetAndPatchProduct() throws Exception {
    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "Test Product",
            "code",
            "test-product-" + UUID.randomUUID().toString().substring(0, 8),
            "tenant",
            "moscow",
            "site",
            "dc1",
            "tags",
            List.of("test"));

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/products")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test Product"))
            .andExpect(jsonPath("$.ciIds").isArray())
            .andReturn();

    String productId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            get("/api/v1/admin/products/" + productId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").exists());

    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Updated Product"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Product"));
  }

  @Test
  void patchProductBindsCiIds() throws Exception {
    String token = obtainAdminToken();
    String ciId = createConfigurationItem(token, "bind-ci-" + UUID.randomUUID() + ".wisla.local");
    String productId = createProduct(token, "bind-product-" + UUID.randomUUID().toString().substring(0, 8));

    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ciIds", List.of(ciId)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ciIds.length()").value(1))
        .andExpect(jsonPath("$.ciIds[0]").value(ciId));
  }

  @Test
  void deleteEmptyProductSucceeds() throws Exception {
    String token = obtainAdminToken();
    String productId = createProduct(token, "delete-me-" + UUID.randomUUID().toString().substring(0, 8));

    mockMvc
        .perform(
            delete("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/v1/admin/products/" + productId).header("Authorization", bearer(token)))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteProductWithLinkedCisReturns409() throws Exception {
    String token = obtainAdminToken();
    String ciId = createConfigurationItem(token, "linked-ci-" + UUID.randomUUID() + ".wisla.local");
    String productId = createProduct(token, "linked-product-" + UUID.randomUUID().toString().substring(0, 8));

    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ciIds", List.of(ciId)))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            delete("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("conflict"));
  }

  @Test
  void createProductWithoutAdminReturns403() throws Exception {
    String operatorToken = obtainOperatorToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "Forbidden Product",
            "code",
            "forbidden-" + UUID.randomUUID().toString().substring(0, 8),
            "tenant",
            "moscow",
            "site",
            "dc1");

    mockMvc
        .perform(
            post("/api/v1/admin/products")
                .header("Authorization", bearer(operatorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("forbidden"));
  }

  @Test
  void duplicateProductCodeReturns409() throws Exception {
    String token = obtainAdminToken();
    String code = "dup-code-" + UUID.randomUUID().toString().substring(0, 8);
    createProduct(token, code);

    Map<String, Object> duplicate =
        Map.of("name", "Another Product", "code", code, "tenant", "moscow", "site", "dc1");

    mockMvc
        .perform(
            post("/api/v1/admin/products")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("conflict"));
  }

  @Test
  void healthDetailReflectsCiBindAfterPatch() throws Exception {
    String token = obtainAdminToken();
    String ciId = createConfigurationItem(token, "health-ci-" + UUID.randomUUID() + ".wisla.local");
    String productId = createProduct(token, "health-product-" + UUID.randomUUID().toString().substring(0, 8));

    mockMvc
        .perform(
            get("/api/v1/health/products/" + productId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ciIds").isEmpty())
        .andExpect(jsonPath("$.configurationItems").isEmpty());

    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ciIds", List.of(ciId)))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/health/products/" + productId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ciIds.length()").value(1))
        .andExpect(jsonPath("$.ciIds[0]").value(ciId))
        .andExpect(jsonPath("$.configurationItems.length()").value(1))
        .andExpect(jsonPath("$.configurationItems[0].id").value(ciId));
  }

  @Test
  void patchComponentsReplacesSlots() throws Exception {
    String token = obtainAdminToken();
    String productId = createProduct(token, "comp-product-" + UUID.randomUUID().toString().substring(0, 8));
    String ciPower = createConfigurationItem(token, "power-" + UUID.randomUUID() + ".wisla.local");
    String ciCpu = createConfigurationItem(token, "cpu-" + UUID.randomUUID() + ".wisla.local");

    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "components",
                    List.of(
                        Map.of("code", "POWER", "name", "POWER", "weight", 20, "influenceType", "critical", "ciIds", List.of(ciPower)),
                        Map.of("code", "CPU", "name", "CPU", "weight", 80, "influenceType", "weighted", "ciIds", List.of(ciCpu))
                    )))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.length()").value(2))
        .andExpect(jsonPath("$.components[0].code").value("POWER"))
        .andExpect(jsonPath("$.components[0].weight").value(20));
  }

  @Test
  void omitComponentsLeavesSlotsUnchanged() throws Exception {
    String token = obtainAdminToken();
    String productId = createProduct(token, "omit-product-" + UUID.randomUUID().toString().substring(0, 8));
    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "components",
                    List.of(Map.of("code", "POWER", "name", "POWER", "weight", 100, "influenceType", "weighted"))))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Renamed"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed"))
        .andExpect(jsonPath("$.components.length()").value(1))
        .andExpect(jsonPath("$.components[0].code").value("POWER"));
  }

  @Test
  void zeroTotalWeightReturns400() throws Exception {
    String token = obtainAdminToken();
    String productId = createProduct(token, "zero-product-" + UUID.randomUUID().toString().substring(0, 8));

    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "components",
                    List.of(Map.of("code", "CPU", "name", "CPU", "weight", 0, "influenceType", "weighted"))))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ciOnTwoComponentsReturns400() throws Exception {
    String token = obtainAdminToken();
    String productId = createProduct(token, "dupci-product-" + UUID.randomUUID().toString().substring(0, 8));
    String ciId = createConfigurationItem(token, "dup-" + UUID.randomUUID() + ".wisla.local");

    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "components",
                    List.of(
                        Map.of("code", "POWER", "name", "POWER", "weight", 50, "influenceType", "critical", "ciIds", List.of(ciId)),
                        Map.of("code", "CPU", "name", "CPU", "weight", 50, "influenceType", "weighted", "ciIds", List.of(ciId))
                    )))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchCiIdsBindsNewCiToCommon() throws Exception {
    String token = obtainAdminToken();
    String ciId = createConfigurationItem(token, "common-ci-" + UUID.randomUUID() + ".wisla.local");
    String productId = createProduct(token, "common-product-" + UUID.randomUUID().toString().substring(0, 8));

    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ciIds", List.of(ciId)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components[?(@.code == 'COMMON')].ciIds[0]").value(ciId));
  }

  @Test
  void patchComponentsWithoutAdminReturns403() throws Exception {
    String operatorToken = obtainOperatorToken();
    String adminToken = obtainAdminToken();
    String productId = createProduct(adminToken, "forbid-comp-" + UUID.randomUUID().toString().substring(0, 8));

    mockMvc
        .perform(
            patch("/api/v1/admin/products/" + productId)
                .header("Authorization", bearer(operatorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "components",
                    List.of(Map.of("code", "CPU", "name", "CPU", "weight", 100, "influenceType", "weighted"))))))
        .andExpect(status().isForbidden());
  }

  private String createProduct(String token, String code) throws Exception {
    Map<String, Object> create =
        Map.of("name", "Product " + code, "code", code, "tenant", "moscow", "site", "dc1");

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/products")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn();

    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
  }

  private String createConfigurationItem(String token, String fqdn) throws Exception {
    Map<String, Object> create =
        Map.of("fqdn", fqdn, "ciType", "node", "system", "Test System");

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/configuration-items")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andReturn();

    return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
  }
}
