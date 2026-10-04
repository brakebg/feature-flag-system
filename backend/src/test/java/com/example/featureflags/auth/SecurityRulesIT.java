package com.example.featureflags.auth;

import static com.example.featureflags.support.SecurityTestSupport.adminToken;
import static com.example.featureflags.support.SecurityTestSupport.bearer;
import static com.example.featureflags.support.SecurityTestSupport.clientToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.IntegrationTest;
import com.example.featureflags.support.MutableClock;
import com.example.featureflags.support.Tokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Spec 5.1, 5.4: resource-server validation and the path rules. */
@IntegrationTest
@Import(SecurityRulesIT.Clocks.class)
class SecurityRulesIT {

  static final String BASE = "https://featureflags.local/problems/";
  static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

  @TestConfiguration
  static class Clocks {
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock(NOW);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired MutableClock clock;

  @BeforeEach
  void resetClock() {
    clock.set(NOW);
  }

  private ResultActions getWith(String path, String token) throws Exception {
    var req = get(path);
    if (token != null) {
      req.header(HttpHeaders.AUTHORIZATION, bearer(token));
    }
    return mvc.perform(req);
  }

  private static void expect401(ResultActions r, String path) throws Exception {
    r.andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", Matchers.startsWith("Bearer")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(BASE + "unauthorized"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.instance").value(path));
  }

  private static void expect403(ResultActions r) throws Exception {
    r.andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(BASE + "forbidden"))
        .andExpect(jsonPath("$.status").value(403));
  }

  private static String signed(String scope, String aud, Instant iat, Instant exp) {
    return Tokens.sign(Tokens.claims("admin", scope, aud, iat, exp));
  }

  @Test
  @Tag("ERR-GET-/admin/groups-401")
  void adminApiWithoutTokenIs401() throws Exception {
    expect401(getWith("/api/v1/admin/groups", null), "/api/v1/admin/groups");
  }

  @Test
  @Tag("ERR-GET-/admin/groups-401")
  void expiredBadlySignedWrongIssuerOrGarbageTokensAre401() throws Exception {
    String expired = signed("admin", "feature-flag-admin", NOW.minusSeconds(60), NOW);
    expect401(getWith("/api/v1/admin/groups", expired), "/api/v1/admin/groups");

    String other =
        Tokens.sign(
            Tokens.claims("admin", "admin", "feature-flag-admin", NOW, NOW.plusSeconds(60)),
            "another-secret-that-is-32-bytes-long!!");
    expect401(getWith("/api/v1/admin/groups", other), "/api/v1/admin/groups");

    Map<String, Object> wrongIss =
        Tokens.claims("admin", "admin", "feature-flag-admin", NOW, NOW.plusSeconds(60));
    wrongIss.put("iss", "someone-else");
    expect401(getWith("/api/v1/admin/groups", Tokens.sign(wrongIss)), "/api/v1/admin/groups");

    String unknownAudience = signed("admin", "another-api", NOW, NOW.plusSeconds(60));
    expect401(getWith("/api/v1/admin/groups", unknownAudience), "/api/v1/admin/groups");

    expect401(getWith("/api/v1/admin/groups", "not-a-jwt"), "/api/v1/admin/groups");
  }

  @Test
  void tokenIsValidUntilOneSecondBeforeExp() throws Exception {
    String token = signed("admin", "feature-flag-admin", NOW, NOW.plusSeconds(10));
    clock.set(NOW.plusSeconds(9));
    getWith("/actuator/prometheus", token).andExpect(status().isOk());
    clock.set(NOW.plusSeconds(10));
    expect401(getWith("/actuator/prometheus", token), "/actuator/prometheus");
  }

  @Test
  @Tag("AC-EVAL-4")
  @Tag("ERR-GET-/admin/groups-403")
  void clientTokenOnAdminApiIs403() throws Exception {
    String client = clientToken(mvc, json);
    expect403(getWith("/api/v1/admin/groups", client));
    expect403(getWith("/api/v1/admin/audit", client));
    expect403(
        mvc.perform(
            post("/api/v1/admin/groups")
                .header(HttpHeaders.AUTHORIZATION, bearer(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")));
  }

  @Test
  @Tag("ERR-GET-/admin/groups-403")
  void adminScopeWithServiceAudienceOrServiceScopeWithAdminAudienceIs403() throws Exception {
    expect403(
        getWith(
            "/api/v1/admin/groups",
            signed("admin", "feature-flag-service", NOW, NOW.plusSeconds(60))));
    expect403(
        getWith(
            "/api/v1/admin/groups",
            signed("flags:read", "feature-flag-admin", NOW, NOW.plusSeconds(60))));
  }

  @Test
  void adminResponsesAreNeverCachedWhateverTheStatus() throws Exception {
    getWith("/api/v1/admin/groups", null).andExpect(header().string("Cache-Control", "no-store"));
    getWith("/api/v1/admin/groups", clientToken(mvc, json))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void prometheusNeedsAnAdminToken() throws Exception {
    expect401(getWith("/actuator/prometheus", null), "/actuator/prometheus");
    expect403(getWith("/actuator/prometheus", clientToken(mvc, json)));
    getWith("/actuator/prometheus", adminToken(mvc, json)).andExpect(status().isOk());
  }

  @Test
  void healthAndInfoArePublic() throws Exception {
    getWith("/actuator/health", null).andExpect(status().isOk());
    getWith("/actuator/health/liveness", null).andExpect(status().isOk());
    getWith("/actuator/health/readiness", null).andExpect(status().isOk());
    getWith("/actuator/info", null).andExpect(status().isOk());
  }

  @Test
  void everythingElseIs401WithoutTokenAnd403WithAValidToken() throws Exception {
    expect401(getWith("/somewhere/else", null), "/somewhere/else");
    expect403(getWith("/somewhere/else", adminToken(mvc, json)));
    expect401(getWith("/actuator/env", null), "/actuator/env");
  }

  @Test
  void corsPreflightFromTheAllowedOrigin() throws Exception {
    mvc.perform(
            options("/api/v1/admin/groups")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PATCH")
                .header(
                    "Access-Control-Request-Headers", "Authorization, Content-Type, If-None-Match"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
        .andExpect(
            header()
                .string(
                    "Access-Control-Allow-Methods",
                    Matchers.allOf(
                        Matchers.containsString("GET"),
                        Matchers.containsString("POST"),
                        Matchers.containsString("PATCH"),
                        Matchers.containsString("DELETE"))))
        .andExpect(
            header()
                .string(
                    "Access-Control-Allow-Headers",
                    Matchers.allOf(
                        Matchers.containsStringIgnoringCase("authorization"),
                        Matchers.containsStringIgnoringCase("content-type"),
                        Matchers.containsStringIgnoringCase("if-none-match"))));
  }

  @Test
  void otherOriginsGetNoAllowOriginHeader() throws Exception {
    mvc.perform(
            options("/api/v1/admin/groups")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    mvc.perform(get("/actuator/health").header("Origin", "http://evil.example"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }
}
