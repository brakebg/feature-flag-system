package com.example.featureflags.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/** Gets real tokens through the public endpoints. */
public final class SecurityTestSupport {

  private SecurityTestSupport() {}

  public static String adminToken(MockMvc mvc) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return TestJson.tree(body).get("accessToken").asText();
  }

  public static String clientToken(MockMvc mvc) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/token")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        basic("order-service", "order-service-dev-secret"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content("grant_type=client_credentials&scope=flags:read"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode n = TestJson.tree(body);
    return n.get("access_token").asText();
  }

  public static String basic(String id, String secret) {
    return "Basic "
        + java.util.Base64.getEncoder()
            .encodeToString((id + ":" + secret).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  public static String bearer(String token) {
    return "Bearer " + token;
  }
}
