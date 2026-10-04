package com.example.featureflags.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Spec 5.2, 5.5: token claims, types and lifetimes. */
class TokenIssuerTest {

  static final String SECRET = "change-me-to-a-32-byte-minimum-secret!!";
  static final Instant NOW = Instant.parse("2026-10-01T14:32:05.987Z");

  static ClientRegistrationProperties props() {
    return new ClientRegistrationProperties(
        "admin",
        "admin123",
        SECRET,
        "feature-flag-service",
        Duration.ofHours(8),
        Duration.ofMinutes(15),
        List.of(new ClientRegistration("order-service", "s", List.of("flags:read"))));
  }

  private final TokenIssuer issuer = new TokenIssuer(props(), Clock.fixed(NOW, ZoneOffset.UTC));

  private static Jwt decode(String token) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    decoder.setJwtValidator(
        jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
    return decoder.decode(token);
  }

  @Test
  void adminTokenHasTheSpecClaims() {
    IssuedToken t = issuer.issueAdmin("admin");
    Jwt jwt = decode(t.value());

    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    assertThat(jwt.getSubject()).isEqualTo("admin");
    assertThat((Object) jwt.getClaim("scope")).isEqualTo("admin");
    assertThat((Object) jwt.getClaim("aud")).isEqualTo(List.of("feature-flag-admin"));
    assertThat((Object) jwt.getClaim("iss")).isEqualTo("feature-flag-service");
    long iat = ((Instant) jwt.getClaim("iat")).getEpochSecond();
    long exp = ((Instant) jwt.getClaim("exp")).getEpochSecond();
    assertThat(iat).isEqualTo(NOW.getEpochSecond());
    assertThat(exp - iat).isEqualTo(8 * 3600);
    assertThat(t.expiresAt()).isEqualTo(Instant.ofEpochSecond(exp));
  }

  @Test
  void claimsAreEncodedWithTheSpecJsonTypes() {
    String payload =
        new String(
            java.util.Base64.getUrlDecoder()
                .decode(issuer.issueAdmin("admin").value().split("\\.")[1]),
            StandardCharsets.UTF_8);
    assertThat(payload)
        .contains("\"aud\":[\"feature-flag-admin\"]")
        .contains("\"scope\":\"admin\"")
        .containsPattern("\"iat\":\\d+[,}]")
        .containsPattern("\"exp\":\\d+[,}]");
  }

  @Test
  void clientTokenHasTheSpecClaims() {
    IssuedToken t = issuer.issueClient("order-service", List.of("flags:read"));
    Jwt jwt = decode(t.value());

    assertThat(jwt.getSubject()).isEqualTo("order-service");
    assertThat((Object) jwt.getClaim("scope")).isEqualTo("flags:read");
    assertThat((Object) jwt.getClaim("aud")).isEqualTo(List.of("feature-flag-service"));
    long iat = ((Instant) jwt.getClaim("iat")).getEpochSecond();
    long exp = ((Instant) jwt.getClaim("exp")).getEpochSecond();
    assertThat(exp - iat).isEqualTo(900);
    assertThat(t.ttlSeconds()).isEqualTo(900);
  }
}
