package ru.wisla.fm.identity.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.wisla.fm.support.AbstractFmModuleTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends AbstractFmModuleTest {

  @Test
  void loginWithValidCredentialsReturnsToken() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        java.util.Map.of("login", ADMIN_LOGIN, "password", ADMIN_PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").isNumber())
        .andExpect(jsonPath("$.user.login").value(ADMIN_LOGIN));
  }

  @Test
  void loginWithInvalidCredentialsReturns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        java.util.Map.of("login", ADMIN_LOGIN, "password", "wrong"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorized"));
  }

  @Test
  void meWithoutTokenReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void meWithValidTokenReturnsUser() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.login").value(ADMIN_LOGIN))
        .andExpect(jsonPath("$.active").value(true));
  }
}
