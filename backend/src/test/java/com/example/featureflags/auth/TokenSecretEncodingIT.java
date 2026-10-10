package com.example.featureflags.auth;

import static com.example.featureflags.support.SecurityTestSupport.basic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.featureflags.support.IntegrationTest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spec 5.5 / D-018: a secret with special characters works sent plain or form-encoded. The client
 * comes from {@code FF_AUTH_CLIENTS_0_*}, the same properties as {@link EnvClientsIT}, so both
 * share one Spring context (one database pool).
 */
@IntegrationTest
@TestPropertySource(
    properties = {
      "FF_AUTH_CLIENTS_0_CLIENT_ID=billing",
      "FF_AUTH_CLIENTS_0_CLIENT_SECRET=a+b%c d",
      "FF_AUTH_CLIENTS_0_SCOPES=flags:read"
    })
class TokenSecretEncodingIT {

  @Autowired MockMvc mvc;

  private int status(String authorization) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/token")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void plainAndFormEncodedSecretsBothWork() throws Exception {
    org.assertj.core.api.Assertions.assertThat(status(basic("billing", "a+b%c d"))).isEqualTo(200);
    org.assertj.core.api.Assertions.assertThat(
            status(basic("billing", URLEncoder.encode("a+b%c d", StandardCharsets.UTF_8))))
        .isEqualTo(200);
    org.assertj.core.api.Assertions.assertThat(status(basic("billing", "a b%c d"))).isEqualTo(401);
  }
}
