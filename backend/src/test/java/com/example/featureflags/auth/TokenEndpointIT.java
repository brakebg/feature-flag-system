package com.example.featureflags.auth;

import static com.example.featureflags.support.SecurityTestSupport.basic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.IntegrationTest;
import com.example.featureflags.support.TestJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Spec 5.5: client credentials token endpoint. */
@IntegrationTest
class TokenEndpointIT {

  static final String GOOD = basic("order-service", "order-service-dev-secret");

  @Autowired MockMvc mvc;

  private ResultActions token(String authorization, String body) throws Exception {
    var req =
        post("/api/v1/auth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).content(body);
    if (authorization != null) {
      req.header(HttpHeaders.AUTHORIZATION, authorization);
    }
    return mvc.perform(req);
  }

  @Test
  @Tag("AC-EVAL-1")
  void validClientGetsABearerTokenWithFlagsRead() throws Exception {
    String body =
        token(GOOD, "grant_type=client_credentials&scope=flags:read")
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Pragma", "no-cache"))
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").value(900))
            .andExpect(jsonPath("$.scope").value("flags:read"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode claims =
        TestJson.tree(
            Base64.getUrlDecoder()
                .decode(TestJson.tree(body).get("access_token").asText().split("\\.")[1]));
    assertThat(claims.get("sub").asText()).isEqualTo("order-service");
    assertThat(claims.get("scope").asText()).isEqualTo("flags:read");
    assertThat(claims.get("aud").get(0).asText()).isEqualTo("feature-flag-service");
    assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(900);
  }

  @Test
  @Tag("AC-EVAL-1")
  void omittedOrEmptyScopeMeansAllRegisteredScopes() throws Exception {
    token(GOOD, "grant_type=client_credentials")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("flags:read"));
    token(GOOD, "grant_type=client_credentials&scope=")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scope").value("flags:read"));
  }

  @Test
  @Tag("AC-EVAL-2")
  @Tag("ERR-POST-/auth/token-401")
  void wrongSecretIsInvalidClientWithBasicChallenge() throws Exception {
    token(basic("order-service", "wrong"), "grant_type=client_credentials")
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic")))
        .andExpect(jsonPath("$.error").value("invalid_client"));
  }

  @Test
  @Tag("ERR-POST-/auth/token-401")
  void missingMalformedOrBodyOnlyCredentialsAreInvalidClient() throws Exception {
    token(null, "grant_type=client_credentials")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_client"));
    token("Basic !!!not-base64", "grant_type=client_credentials")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_client"));
    token(
            "Basic " + Base64.getEncoder().encodeToString("no-colon".getBytes()),
            "grant_type=client_credentials")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_client"));
    token(
            null,
            "grant_type=client_credentials&client_id=order-service&client_secret=order-service-dev-secret")
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic")))
        .andExpect(jsonPath("$.error").value("invalid_client"));
    token(basic("unknown", "x"), "grant_type=client_credentials")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("invalid_client"));
  }

  @Test
  @Tag("ERR-POST-/auth/token-401")
  void bearerHeaderOnTheTokenEndpointIsInvalidClient() throws Exception {
    token("Bearer garbage", "grant_type=client_credentials")
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Basic")))
        .andExpect(jsonPath("$.error").value("invalid_client"));
  }

  @Test
  void bodyAbove65536BytesIs413() throws Exception {
    token(GOOD, "grant_type=client_credentials&pad=" + "x".repeat(65_600))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(
            jsonPath("$.type").value("https://featureflags.local/problems/payload-too-large"));
  }

  @Test
  @Tag("AC-EVAL-2")
  @Tag("ERR-POST-/auth/token-400")
  void unknownGrantTypeIsUnsupported() throws Exception {
    token(GOOD, "grant_type=password")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
  }

  @Test
  @Tag("AC-EVAL-2")
  @Tag("ERR-POST-/auth/token-400")
  void adminScopeIsInvalidScope() throws Exception {
    token(GOOD, "grant_type=client_credentials&scope=admin")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_scope"));
    token(GOOD, "grant_type=client_credentials&scope=flags:read%20admin")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_scope"));
  }

  @Test
  @Tag("ERR-POST-/auth/token-400")
  void missingGrantTypeOrWrongContentTypeIsInvalidRequest() throws Exception {
    token(GOOD, "scope=flags:read")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_request"));
    mvc.perform(
            post("/api/v1/auth/token")
                .header(HttpHeaders.AUTHORIZATION, GOOD)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grant_type\":\"client_credentials\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_request"));
  }
}
