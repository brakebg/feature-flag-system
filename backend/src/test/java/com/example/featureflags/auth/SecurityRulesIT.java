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
import com.example.featureflags.support.TestJson;
import com.example.featureflags.support.Tokens;
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
    String client = clientToken(mvc);
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
  @Tag("AC-EVAL-3")
  @Tag("ERR-GET-/evaluate/flags-401")
  @Tag("ERR-GET-/evaluate/groups/{groupKey}-401")
  @Tag("ERR-GET-/evaluate/flags/{groupKey}/{flagKey}-401")
  void evaluationWithoutOrWithExpiredTokenIs401() throws Exception {
    for (String path :
        new String[] {
          "/api/v1/evaluate/flags",
          "/api/v1/evaluate/groups/orders",
          "/api/v1/evaluate/flags/orders/new-checkout"
        }) {
      expect401(getWith(path, null), path);
      String expired =
          Tokens.sign(
              Tokens.claims(
                  "order-service",
                  "flags:read",
                  "feature-flag-service",
                  NOW.minusSeconds(60),
                  NOW));
      expect401(getWith(path, expired), path);
    }
  }

  @Test
  @Tag("AC-EVAL-3")
  @Tag("ERR-GET-/evaluate/flags-403")
  @Tag("ERR-GET-/evaluate/groups/{groupKey}-403")
  @Tag("ERR-GET-/evaluate/flags/{groupKey}/{flagKey}-403")
  void evaluationWithAdminTokenOrWrongScopeOrAudienceIs403() throws Exception {
    String admin = adminToken(mvc);
    String serviceNoScope = signed("other", "feature-flag-service", NOW, NOW.plusSeconds(60));
    String readWithAdminAud = signed("flags:read", "feature-flag-admin", NOW, NOW.plusSeconds(60));
    for (String path :
        new String[] {
          "/api/v1/evaluate/flags",
          "/api/v1/evaluate/groups/orders",
          "/api/v1/evaluate/flags/orders/new-checkout"
        }) {
      expect403(getWith(path, admin));
      expect403(getWith(path, serviceNoScope));
      expect403(getWith(path, readWithAdminAud));
    }
  }

  @Test
  void evaluationWithClientTokenPassesSecurity() throws Exception {
    getWith("/api/v1/evaluate/flags", clientToken(mvc)).andExpect(status().isOk());
  }

  @Test
  void tokensWithOtherAlgorithmsOrMissingClaimsAre401() throws Exception {
    Map<String, Object> noExp =
        Tokens.claims("admin", "admin", "feature-flag-admin", NOW, NOW.plusSeconds(60));
    noExp.remove("exp");
    expect401(getWith("/api/v1/admin/groups", Tokens.sign(noExp)), "/api/v1/admin/groups");
    Map<String, Object> noAud =
        Tokens.claims("admin", "admin", "feature-flag-admin", NOW, NOW.plusSeconds(60));
    noAud.remove("aud");
    expect401(getWith("/api/v1/admin/groups", Tokens.sign(noAud)), "/api/v1/admin/groups");
    String hs512 =
        Tokens.sign(
            Tokens.claims("admin", "admin", "feature-flag-admin", NOW, NOW.plusSeconds(60)),
            Tokens.DEV_SECRET + Tokens.DEV_SECRET,
            com.nimbusds.jose.JWSAlgorithm.HS512);
    expect401(getWith("/api/v1/admin/groups", hs512), "/api/v1/admin/groups");
    String none =
        java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes())
            + "."
            + java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    TestJson.bytes(
                        Tokens.claims(
                            "admin", "admin", "feature-flag-admin", NOW, NOW.plusSeconds(60))))
            + ".";
    expect401(getWith("/api/v1/admin/groups", none), "/api/v1/admin/groups");
  }

  @Test
  void publicPathsIgnoreAStaleBearerHeader() throws Exception {
    getWith("/actuator/health", "garbage").andExpect(status().isOk());
    getWith("/actuator/info", "garbage").andExpect(status().isOk());
  }

  @Test
  void springSecurityDefaultHeadersStayAndAdminCacheControlIsOnlyNoStore() throws Exception {
    getWith("/actuator/health", null)
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Cache-Control", Matchers.containsString("no-cache")));
    getWith("/api/v1/admin/groups", adminToken(mvc))
        .andExpect(header().stringValues("Cache-Control", "no-store"))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"));
  }

  @Test
  void encodedAdminPathStillGetsNoStore() throws Exception {
    mvc.perform(get(java.net.URI.create("/api/v1/%61dmin/groups")))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void adminResponsesAreNeverCachedWhateverTheStatus() throws Exception {
    getWith("/api/v1/admin/groups", null).andExpect(header().string("Cache-Control", "no-store"));
    getWith("/api/v1/admin/groups", clientToken(mvc))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void prometheusNeedsAnAdminToken() throws Exception {
    expect401(getWith("/actuator/prometheus", null), "/actuator/prometheus");
    expect403(getWith("/actuator/prometheus", clientToken(mvc)));
    getWith("/actuator/prometheus", adminToken(mvc)).andExpect(status().isOk());
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
    expect403(getWith("/somewhere/else", adminToken(mvc)));
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
            r ->
                org.assertj.core.api.Assertions.assertThat(
                        set(r.getResponse().getHeader("Access-Control-Allow-Methods")))
                    .containsExactlyInAnyOrder("get", "post", "patch", "delete"))
        .andExpect(
            r ->
                org.assertj.core.api.Assertions.assertThat(
                        set(r.getResponse().getHeader("Access-Control-Allow-Headers")))
                    .containsExactlyInAnyOrder("authorization", "content-type", "if-none-match"));
  }

  private static java.util.Set<String> set(String header) {
    java.util.Set<String> out = new java.util.HashSet<>();
    for (String part : header.split(",")) {
      out.add(part.strip().toLowerCase(java.util.Locale.ROOT));
    }
    return out;
  }

  @Test
  void preflightAskingForAnotherHeaderDoesNotGetIt() throws Exception {
    mvc.perform(
            options("/api/v1/admin/groups")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "X-Other"))
        .andExpect(
            r ->
                org.assertj.core.api.Assertions.assertThat(
                        r.getResponse().getHeader("Access-Control-Allow-Headers"))
                    .isNull());
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
