package com.example.featureflags.auth;

import static com.example.featureflags.support.SecurityTestSupport.basic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.featureflags.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec 5.1 / ESC-004 item 2: a client from {@code FF_AUTH_CLIENTS_0_*} is added to the clients from
 * {@code application.yml}; both get tokens. Same properties as {@link TokenSecretEncodingIT}, so
 * both share one Spring context (one database pool).
 */
@IntegrationTest
@TestPropertySource(
    properties = {
      "FF_AUTH_CLIENTS_0_CLIENT_ID=billing",
      "FF_AUTH_CLIENTS_0_CLIENT_SECRET=a+b%c d",
      "FF_AUTH_CLIENTS_0_SCOPES=flags:read"
    })
class EnvClientsIT {

  @Autowired MockMvc mvc;

  private int status(String clientId, String secret) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/token")
                .header(HttpHeaders.AUTHORIZATION, basic(clientId, secret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void envClientAndConfiguredClientBothGetTokens() throws Exception {
    assertThat(status("billing", "a+b%c d")).isEqualTo(200);
    assertThat(status("order-service", "order-service-dev-secret")).isEqualTo(200);
  }
}
