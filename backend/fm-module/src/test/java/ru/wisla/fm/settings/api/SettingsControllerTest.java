package ru.wisla.fm.settings.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ru.wisla.fm.support.AbstractFmModuleTest;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettingsControllerTest extends AbstractFmModuleTest {

  @Test
  void getSettingsWithoutAuthReturns401() throws Exception {
    mockMvc.perform(get("/api/v1/settings")).andExpect(status().isUnauthorized());
  }

  @Test
  void getSettingsReturnsModuleAndProfile() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/settings").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.module.timezone").exists())
        .andExpect(jsonPath("$.module.pollingIntervalSec").isNumber())
        .andExpect(jsonPath("$.profile").exists());
  }

  @Test
  void patchProfileSettings() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(
            patch("/api/v1/settings")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("profile", Map.of("sidebarCollapsed", true)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.sidebarCollapsed").value(true));
  }

  @Test
  void patchModuleSettingsAsAdmin() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(
            patch("/api/v1/settings")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("module", Map.of("timezone", "Europe/Moscow")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.module.timezone").value("Europe/Moscow"));
  }

  @Test
  void patchModuleSettingsWithoutAdminReturns403() throws Exception {
    String operatorToken = obtainOperatorToken();
    mockMvc
        .perform(
            patch("/api/v1/settings")
                .header("Authorization", bearer(operatorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("module", Map.of("timezone", "UTC")))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("forbidden"));
  }

  @Test
  void getAndPatchNotificationSettings() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/settings/notifications").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailEnabled").isBoolean())
        .andExpect(jsonPath("$.rules").isArray());

    mockMvc
        .perform(
            patch("/api/v1/settings/notifications")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("emailEnabled", true, "telegramEnabled", false, "rules", List.of()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailEnabled").value(true));
  }

  @Test
  void patchNotificationSettingsWithoutAdminReturns403() throws Exception {
    String operatorToken = obtainOperatorToken();
    mockMvc
        .perform(
            patch("/api/v1/settings/notifications")
                .header("Authorization", bearer(operatorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("emailEnabled", true))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("forbidden"));
  }

  @Test
  void getAndPatchIntegrationSettings() throws Exception {
    String token = obtainAdminToken();
    mockMvc
        .perform(get("/api/v1/settings/integrations").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.wisla").exists())
        .andExpect(jsonPath("$.itsm").exists());

    mockMvc
        .perform(
            patch("/api/v1/settings/integrations")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "wisla",
                            Map.of("enabled", true, "baseUrl", "https://wisla.example"),
                            "itsm",
                            Map.of("enabled", false)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.wisla.enabled").value(true))
        .andExpect(jsonPath("$.wisla.baseUrl").value("https://wisla.example"));
  }

  @Test
  void patchIntegrationSettingsWithoutAdminReturns403() throws Exception {
    String operatorToken = obtainOperatorToken();
    mockMvc
        .perform(
            patch("/api/v1/settings/integrations")
                .header("Authorization", bearer(operatorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("wisla", Map.of("enabled", true)))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("forbidden"));
  }

}
