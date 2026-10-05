package com.example.featureflags.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.featureflags.support.DatabaseCleaner;
import com.example.featureflags.support.PostgresContainerConfig;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec 9.2: any body above 65,536 bytes gets 413, also when it is sent chunked (no Content-Length)
 * as a form to the token endpoint (SF-C1). Runs against the real Tomcat, because the form body is
 * parsed by the container, not through the request input stream.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, DatabaseCleaner.class})
class ChunkedBodyIT {

  @LocalServerPort int port;
  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> chunkedToken(String body) throws Exception {
    return token(body, "application/x-www-form-urlencoded", true);
  }

  private HttpResponse<String> token(String body, String type, boolean chunked) throws Exception {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    String basic =
        Base64.getEncoder()
            .encodeToString(
                "order-service:order-service-dev-secret".getBytes(StandardCharsets.UTF_8));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/token"))
            .header("Authorization", "Basic " + basic)
            .header("Content-Type", type)
            // An input-stream publisher has no known length, so the body is sent chunked.
            .POST(
                chunked
                    ? HttpRequest.BodyPublishers.ofInputStream(
                        () -> new ByteArrayInputStream(bytes))
                    : HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void chunkedFormBodyAbove65536BytesIs413() throws Exception {
    HttpResponse<String> res =
        chunkedToken("grant_type=client_credentials&pad=" + "x".repeat(70_000));
    assertThat(res.statusCode()).isEqualTo(413);
    assertThat(res.headers().firstValue("Content-Type")).hasValue("application/problem+json");
    assertThat(res.body())
        .contains("\"type\":\"https://featureflags.local/problems/payload-too-large\"");
  }

  @Test
  void chunkedFormBodyWithinTheLimitStillWorks() throws Exception {
    HttpResponse<String> res = chunkedToken("grant_type=client_credentials&scope=flags:read");
    assertThat(res.statusCode()).isEqualTo(200);
    assertThat(res.body()).contains("\"expires_in\":900");
  }

  @Test
  void badFormEncodingIsHandledLikeTheContainerDoes() throws Exception {
    // FR-7 / N1: a bad percent escape or an unknown charset is client input, never a 500, and a
    // chunked body gives the same answer as the same body with a Content-Length.
    String[][] cases = {
      {"grant_type=client_credentials&scope=%zz", "application/x-www-form-urlencoded"},
      {"grant_type=client_credentials&x=%", "application/x-www-form-urlencoded"},
      {"grant_type=client_credentials", "application/x-www-form-urlencoded; charset=bogus"},
    };
    for (String[] c : cases) {
      HttpResponse<String> plain = token(c[0], c[1], false);
      HttpResponse<String> chunked = token(c[0], c[1], true);
      assertThat(plain.statusCode()).as(c[0] + " " + c[1]).isLessThan(500);
      assertThat(chunked.statusCode()).as(c[0] + " " + c[1]).isEqualTo(plain.statusCode());
      if (plain.statusCode() != 200) {
        assertThat(chunked.body()).as(c[0] + " " + c[1]).isEqualTo(plain.body());
      }
    }
  }
}
