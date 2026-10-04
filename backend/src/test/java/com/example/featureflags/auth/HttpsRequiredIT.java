package com.example.featureflags.auth;

import static com.example.featureflags.support.SecurityTestSupport.basic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Spec 10.2: with FF_REQUIRE_HTTPS=true, login and token need X-Forwarded-Proto: https. */
@IntegrationTest
@TestPropertySource(properties = "FF_REQUIRE_HTTPS=true")
class HttpsRequiredIT {

  static final String TYPE = "https://featureflags.local/problems/https-required";

  @Autowired MockMvc mvc;

  private static MockHttpServletRequestBuilder login() {
    return post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"admin\",\"password\":\"admin123\"}");
  }

  private static MockHttpServletRequestBuilder token() {
    return post("/api/v1/auth/token")
        .header(HttpHeaders.AUTHORIZATION, basic("order-service", "order-service-dev-secret"))
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .content("grant_type=client_credentials");
  }

  @Test
  @Tag("AC-OPS-4")
  @Tag("ERR-POST-/auth/login-403")
  void loginOverPlainHttpIsRejected() throws Exception {
    mvc.perform(login().header("X-Forwarded-Proto", "http"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(TYPE))
        .andExpect(jsonPath("$.status").value(403));
    mvc.perform(login())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TYPE));
  }

  @Test
  @Tag("AC-OPS-4")
  @Tag("ERR-POST-/auth/token-403")
  void tokenOverPlainHttpIsRejectedWithAProblemDetail() throws Exception {
    mvc.perform(token().header("X-Forwarded-Proto", "http"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(TYPE));
  }

  @Test
  @Tag("AC-OPS-4")
  void percentEncodedPathsAreCheckedToo() throws Exception {
    mvc.perform(
            post(java.net.URI.create("/api/v1/auth/%6cogin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}")
                .header("X-Forwarded-Proto", "http"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TYPE));
    mvc.perform(
            post(java.net.URI.create("/api/v1/auth/t%6Fken"))
                .header(
                    HttpHeaders.AUTHORIZATION, basic("order-service", "order-service-dev-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials")
                .header("X-Forwarded-Proto", "http"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(TYPE));
  }

  @Test
  void otherPathsStillWorkOverPlainHttp() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/actuator/health")
                .header("X-Forwarded-Proto", "http"))
        .andExpect(status().isOk());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/admin/groups")
                .header("X-Forwarded-Proto", "http"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("https://featureflags.local/problems/unauthorized"));
  }

  @Test
  @Tag("AC-OPS-4")
  void sameRequestsOverHttpsSucceed() throws Exception {
    mvc.perform(login().header("X-Forwarded-Proto", "https")).andExpect(status().isOk());
    mvc.perform(token().header("X-Forwarded-Proto", "https")).andExpect(status().isOk());
  }
}
