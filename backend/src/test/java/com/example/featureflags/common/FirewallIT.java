package com.example.featureflags.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.featureflags.support.DatabaseCleaner;
import com.example.featureflags.support.PostgresContainerConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spec 9.1: requests that Spring Security's firewall rejects before any controller (BF-3) still get
 * a problem detail. Runs against the real Tomcat, where the firewall sees the raw path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, DatabaseCleaner.class})
class FirewallIT {

  @LocalServerPort int port;
  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void firewallRejectedPathsGetAProblemDetail() throws Exception {
    for (String path : new String[] {"/api/v1//evaluate/flags", "/api/v1/admin/groups;x=1"}) {
      HttpResponse<String> res =
          http.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(res.statusCode()).as(path).isEqualTo(400);
      assertThat(res.headers().firstValue("Content-Type")).hasValue("application/problem+json");
      assertThat(res.body())
          .as(path)
          .contains("\"type\":\"https://featureflags.local/problems/malformed-request\"")
          .contains("\"status\":400");
    }
  }
}
