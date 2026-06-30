package ru.wisla.fm.admin.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest extends AbstractFmModuleTest {

  @Test
  void listUsersWithoutAuthReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
  }

  @Test
  void listUsersReturnsSeededAdmin() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/admin/users").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.page.totalElements").isNumber());
  }

  @Test
  void createGetAndPatchUser() throws Exception {
    String token = obtainAdminToken();
    String roleId = firstRoleId(token);

    Map<String, Object> create =
        Map.of(
            "login",
            "test-operator",
            "fullName",
            "Test Operator",
            "email",
            "operator@wisla.local",
            "password",
            "secret123",
            "roleIds",
            List.of(roleId),
            "team",
            "NOC");

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/users")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.login").value("test-operator"))
            .andReturn();

    String userId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(get("/api/v1/admin/users/" + userId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Test Operator"));

    mockMvc
        .perform(
            patch("/api/v1/admin/users/" + userId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("fullName", "Updated Operator"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Updated Operator"));
  }

  @Test
  void createUserWithoutAdminReturns403() throws Exception {
    String operatorToken = obtainOperatorToken();
    String roleId = firstRoleId(obtainAdminToken());

    Map<String, Object> create =
        Map.of(
            "login",
            "forbidden-user",
            "fullName",
            "Forbidden",
            "email",
            "forbidden@wisla.local",
            "password",
            "secret123",
            "roleIds",
            List.of(roleId));

    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .header("Authorization", bearer(operatorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("forbidden"));
  }

  @Test
  void listRolesReturnsSeededRole() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/admin/roles").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$[0].permissions").isArray());
  }

  @Test
  void createGetAndPatchRole() throws Exception {
    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "name",
            "Test Role",
            "description",
            "Test permissions",
            "permissions",
            List.of("events", "console"));

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/roles")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test Role"))
            .andReturn();

    String roleId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(get("/api/v1/admin/roles/" + roleId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions").isArray());

    mockMvc
        .perform(
            patch("/api/v1/admin/roles/" + roleId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("description", "Updated description"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("Updated description"));
  }

  @Test
  void createRoleWithoutAdminReturns403() throws Exception {
    String operatorToken = obtainOperatorToken();
    Map<String, Object> create =
        Map.of("name", "Forbidden Role", "permissions", List.of("events"));

    mockMvc
        .perform(
            post("/api/v1/admin/roles")
                .header("Authorization", bearer(operatorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("forbidden"));
  }

  @Test
  void listConfigurationItemsReturnsSeededCi() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(
            get("/api/v1/admin/configuration-items").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.items[0].fqdn").exists());
  }

  @Test
  void createGetAndPatchConfigurationItem() throws Exception {
    String token = obtainAdminToken();
    Map<String, Object> create =
        Map.of(
            "fqdn",
            "test-ci.wisla.local",
            "ciType",
            "node",
            "system",
            "Test System",
            "subsystem",
            "API",
            "tags",
            List.of("test"));

    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/admin/configuration-items")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(create)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fqdn").value("test-ci.wisla.local"))
            .andReturn();

    String ciId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            get("/api/v1/admin/configuration-items/" + ciId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.system").value("Test System"));

    mockMvc
        .perform(
            patch("/api/v1/admin/configuration-items/" + ciId)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("system", "Updated System"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.system").value("Updated System"));

    mockMvc
        .perform(
            delete("/api/v1/admin/configuration-items/" + ciId)
                .header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
  }

  @Test
  void createConfigurationItemWithoutAdminReturns403() throws Exception {
    String operatorToken = obtainOperatorToken();
    Map<String, Object> create =
        Map.of("fqdn", "forbidden-ci.wisla.local", "ciType", "node", "system", "Forbidden");

    mockMvc
        .perform(
            post("/api/v1/admin/configuration-items")
                .header("Authorization", bearer(operatorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(create)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("forbidden"));
  }

  private String firstRoleId(String token) throws Exception {
    MvcResult roles =
        mockMvc
            .perform(get("/api/v1/admin/roles").header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(roles.getResponse().getContentAsString()).get(0).get("id").asText();
  }

}
