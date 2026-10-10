package com.example.featureflags.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.IntegrationTest;
import com.example.featureflags.support.TestJson;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/** Spec 5.2: admin login. */
@IntegrationTest
class LoginIT {

  static final String BASE = "https://featureflags.local/problems/";

  @Autowired MockMvc mvc;

  private org.springframework.test.web.servlet.ResultActions login(String body) throws Exception {
    return mvc.perform(
        post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  void validCredentialsReturnAnAdminToken() throws Exception {
    String body =
        login("{\"username\":\"admin\",\"password\":\"admin123\"}")
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.username").value("admin"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode n = TestJson.tree(body);
    JsonNode claims =
        TestJson.tree(Base64.getUrlDecoder().decode(n.get("accessToken").asText().split("\\.")[1]));
    assertThat(claims.get("sub").asText()).isEqualTo("admin");
    assertThat(claims.get("scope").asText()).isEqualTo("admin");
    assertThat(claims.get("aud").isArray()).isTrue();
    assertThat(claims.get("aud").get(0).asText()).isEqualTo("feature-flag-admin");
    assertThat(claims.get("aud").size()).isEqualTo(1);
    assertThat(claims.get("iss").asText()).isEqualTo("feature-flag-service");
    assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(8 * 3600);
    assertThat(Instant.parse(n.get("expiresAt").asText()))
        .isEqualTo(Instant.ofEpochSecond(claims.get("exp").asLong()));
  }

  @Test
  @Tag("ERR-POST-/auth/login-401")
  void wrongPasswordAndUnknownUserGiveTheSameGeneric401() throws Exception {
    for (String body :
        new String[] {
          "{\"username\":\"admin\",\"password\":\"wrong\"}",
          "{\"username\":\"nobody\",\"password\":\"admin123\"}"
        }) {
      login(body)
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value(BASE + "unauthorized"))
          .andExpect(jsonPath("$.status").value(401))
          .andExpect(jsonPath("$.detail").value("Invalid username or password"))
          .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"));
    }
  }

  @Test
  @Tag("ERR-POST-/auth/login-400")
  void missingOrBlankFieldsAreValidationErrors() throws Exception {
    login("{\"password\":\"admin123\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "validation"))
        .andExpect(jsonPath("$.errors[0].field").value("username"));
    login("{\"username\":\"admin\",\"password\":\"  \"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "validation"))
        .andExpect(jsonPath("$.errors[0].field").value("password"));
  }

  @Test
  @Tag("ERR-POST-/auth/login-400")
  void blankUsernameAndMissingPasswordAreValidationErrors() throws Exception {
    login("{\"username\":\"  \",\"password\":\"admin123\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "validation"))
        .andExpect(jsonPath("$.errors[0].field").value("username"));
    login("{\"username\":\"admin\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "validation"))
        .andExpect(jsonPath("$.errors[0].field").value("password"));
  }

  @Test
  void bodyAbove65536BytesIs413() throws Exception {
    String big = "{\"username\":\"admin\",\"password\":\"" + "x".repeat(65_600) + "\"}";
    login(big)
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.type").value(BASE + "payload-too-large"));
  }

  @Test
  void staleBearerHeaderDoesNotBlockLogin() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .header("Authorization", "Bearer garbage")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
        .andExpect(status().isOk());
  }

  @Test
  @Tag("ERR-POST-/auth/login-400")
  void invalidJsonOrWrongTypesAreMalformed() throws Exception {
    login("{nope")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "malformed-request"));
    login("{\"username\":1,\"password\":\"x\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(BASE + "malformed-request"));
  }

  @Test
  void responseIsNotCached() throws Exception {
    login("{\"username\":\"admin\",\"password\":\"admin123\"}")
        .andExpect(header().string("Cache-Control", "no-store"));
  }
}
