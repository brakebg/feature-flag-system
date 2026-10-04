package com.example.featureflags.auth;

import static com.example.featureflags.support.SecurityTestSupport.basic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.StandaloneMvc;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Spec 5.5: token endpoint rules, without a Spring context (fast; used by PIT, gate 7). */
class TokenControllerTest {

  static final ClientRegistrationProperties PROPS =
      new ClientRegistrationProperties(
          "admin",
          "admin123",
          TokenIssuerTest.SECRET,
          "feature-flag-service",
          Duration.ofHours(8),
          Duration.ofMinutes(15),
          List.of(
              new ClientRegistration("order-service", "secret", List.of("flags:read")),
              new ClientRegistration("multi", "m+1", List.of("flags:read", "other"))));

  private final MockMvc mvc =
      StandaloneMvc.of(
          new TokenController(
              new ConfigClientAuthenticator(PROPS),
              new TokenIssuer(
                  PROPS, Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC))));

  private ResultActions token(String auth, String contentType, String body) throws Exception {
    var req = post("/api/v1/auth/token").content(body);
    if (contentType != null) {
      req.contentType(contentType);
    }
    if (auth != null) {
      req.header(HttpHeaders.AUTHORIZATION, auth);
    }
    return mvc.perform(req);
  }

  private ResultActions form(String auth, String body) throws Exception {
    return token(auth, MediaType.APPLICATION_FORM_URLENCODED_VALUE, body);
  }

  private static void oauthError(ResultActions r, int status, String error) throws Exception {
    r.andExpect(status().is(status))
        .andExpect(jsonPath("$.error").value(error))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Pragma", "no-cache"));
  }

  @Test
  void success() throws Exception {
    form(basic("order-service", "secret"), "grant_type=client_credentials&scope=flags:read")
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Pragma", "no-cache"))
        .andExpect(jsonPath("$.token_type").value("Bearer"))
        .andExpect(jsonPath("$.expires_in").value(900))
        .andExpect(jsonPath("$.scope").value("flags:read"))
        .andExpect(jsonPath("$.access_token").isString());
  }

  @Test
  void scopes() throws Exception {
    form(basic("multi", "m+1"), "grant_type=client_credentials")
        .andExpect(jsonPath("$.scope").value("flags:read other"));
    form(basic("multi", "m+1"), "grant_type=client_credentials&scope=other")
        .andExpect(jsonPath("$.scope").value("other"));
    form(basic("multi", "m+1"), "grant_type=client_credentials&scope=other%20other%20flags:read")
        .andExpect(jsonPath("$.scope").value("other flags:read"));
    form(basic("multi", "m+1"), "grant_type=client_credentials&scope=%20%20")
        .andExpect(jsonPath("$.scope").value("flags:read other"));
    oauthError(
        form(basic("multi", "m+1"), "grant_type=client_credentials&scope=admin"),
        400,
        "invalid_scope");
  }

  @Test
  void clientAuthentication() throws Exception {
    oauthError(form(null, "grant_type=client_credentials"), 401, "invalid_client");
    oauthError(
        form(basic("order-service", "wrong"), "grant_type=client_credentials"),
        401,
        "invalid_client");
    oauthError(form("Bearer x", "grant_type=client_credentials"), 401, "invalid_client");
    oauthError(form("Basic %%%", "grant_type=client_credentials"), 401, "invalid_client");
    oauthError(
        form(
            "Basic " + java.util.Base64.getEncoder().encodeToString("nocolon".getBytes()),
            "grant_type=client_credentials"),
        401,
        "invalid_client");
    form(basic("order-service", "wrong"), "grant_type=client_credentials")
        .andExpect(header().string("WWW-Authenticate", "Basic realm=\"feature-flag-service\""));
    form(
            "basic "
                + java.util.Base64.getEncoder().encodeToString("order-service:secret".getBytes()),
            "grant_type=client_credentials")
        .andExpect(status().isOk());
    form(basic("multi", "m%2B1"), "grant_type=client_credentials").andExpect(status().isOk());
    oauthError(form(basic("multi", "m 1"), "grant_type=client_credentials"), 401, "invalid_client");
  }

  @Test
  void grantTypeAndContentType() throws Exception {
    oauthError(form(basic("order-service", "secret"), "scope=flags:read"), 400, "invalid_request");
    oauthError(form(basic("order-service", "secret"), "grant_type="), 400, "invalid_request");
    oauthError(
        form(basic("order-service", "secret"), "grant_type=password"),
        400,
        "unsupported_grant_type");
    oauthError(
        token(basic("order-service", "secret"), "application/json", "{}"), 400, "invalid_request");
    oauthError(
        token(basic("order-service", "secret"), null, "grant_type=client_credentials"),
        400,
        "invalid_request");
    oauthError(
        token(basic("order-service", "secret"), "not a type", "grant_type=client_credentials"),
        400,
        "invalid_request");
  }
}
