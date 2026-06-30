package ru.wisla.fm.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.wisla.fm.config.DevDataSeeder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractFmModuleTest {

  protected static final String ADMIN_LOGIN = "admin";
  protected static final String ADMIN_PASSWORD = "admin";
  protected static final String DEMO_SOURCE_KEY = DevDataSeeder.DEMO_SOURCE_API_KEY;

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;

  protected String obtainAdminToken() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            java.util.Map.of("login", ADMIN_LOGIN, "password", ADMIN_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    return body.get("accessToken").asText();
  }

  protected String bearer(String token) {
    return "Bearer " + token;
  }

  protected String obtainOperatorToken() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String adminToken = obtainAdminToken();
    Map<String, Object> roleCreate =
        Map.of("name", "Operator-" + suffix, "permissions", List.of("events", "console"));

    MvcResult roleResult =
        mockMvc
            .perform(
                post("/api/v1/admin/roles")
                    .header("Authorization", bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(roleCreate)))
            .andExpect(status().isCreated())
            .andReturn();
    String roleId =
        objectMapper.readTree(roleResult.getResponse().getContentAsString()).get("id").asText();

    String login = "operator-" + suffix;
    Map<String, Object> userCreate =
        Map.of(
            "login",
            login,
            "fullName",
            "NOC Operator",
            "email",
            login + "@wisla.local",
            "password",
            "operator123",
            "roleIds",
            List.of(roleId));

    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userCreate)))
        .andExpect(status().isCreated());

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("login", login, "password", "operator123"))))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(loginResult.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }
}
