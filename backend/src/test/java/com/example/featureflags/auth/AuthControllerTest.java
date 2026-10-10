package com.example.featureflags.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.StandaloneMvc;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Spec 5.2: login rules without a Spring context (fast; used by PIT, gate 7). */
class AuthControllerTest {

  private final MockMvc mvc =
      StandaloneMvc.of(
          new AuthController(
              new ConfigAdminAuthenticator(TokenControllerTest.PROPS),
              new TokenIssuer(
                  TokenControllerTest.PROPS,
                  Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC))));

  @Test
  void loginReturnsTokenExpiryAndUsername() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.username").value("admin"))
        .andExpect(jsonPath("$.expiresAt").value("2026-10-01T08:00:00Z"))
        .andExpect(jsonPath("$.accessToken").isString());
  }

  @Test
  void wrongCredentialsAreTheGeneric401() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("https://featureflags.local/problems/unauthorized"))
        .andExpect(jsonPath("$.detail").value("Invalid username or password"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"));
  }
}
