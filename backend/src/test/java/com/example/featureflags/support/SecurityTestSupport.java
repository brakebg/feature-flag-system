package com.example.featureflags.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Gets real tokens through the public endpoints. */
public final class SecurityTestSupport {

  private SecurityTestSupport() {}

  public static String adminToken(MockMvc mvc, ObjectMapper json) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("accessToken").asText();
  }

  public static String clientToken(MockMvc mvc, ObjectMapper json) throws Exception {
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
    JsonNode n = json.readTree(body);
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
