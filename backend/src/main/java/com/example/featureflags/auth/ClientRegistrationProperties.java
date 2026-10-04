package com.example.featureflags.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spec 5.1: {@code featureflags.auth}. Startup fails if the JWT secret is shorter than 32 bytes
 * (UTF-8), if two clients share a client id, or if a token TTL is below 1 second.
 */
@ConfigurationProperties("featureflags.auth")
public record ClientRegistrationProperties(
    String adminUsername,
    String adminPassword,
    String jwtSecret,
    String issuer,
    Duration adminTokenTtl,
    Duration clientTokenTtl,
    List<ClientRegistration> clients) {

  static final int MIN_SECRET_BYTES = 32;

  public ClientRegistrationProperties {
    clients = clients == null ? List.of() : List.copyOf(clients);
    if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalArgumentException(
          "featureflags.auth.jwt-secret must be at least " + MIN_SECRET_BYTES + " bytes");
    }
    requireTtl("admin-token-ttl", adminTokenTtl);
    requireTtl("client-token-ttl", clientTokenTtl);
    Set<String> ids = new HashSet<>();
    for (ClientRegistration c : clients) {
      if (c.clientId() == null || c.clientId().isEmpty() || !ids.add(c.clientId())) {
        throw new IllegalArgumentException(
            "featureflags.auth.clients: duplicate or empty client-id " + c.clientId());
      }
    }
  }

  private static void requireTtl(String name, Duration ttl) {
    if (ttl == null || ttl.compareTo(Duration.ofSeconds(1)) < 0) {
      throw new IllegalArgumentException(
          "featureflags.auth." + name + " must be at least 1 second");
    }
  }

  /** The secret is never part of {@code toString()} (spec 9.3: never log secrets). */
  @Override
  public String toString() {
    return "ClientRegistrationProperties[adminUsername="
        + adminUsername
        + ", issuer="
        + issuer
        + ", clients="
        + clients.stream().map(ClientRegistration::clientId).toList()
        + "]";
  }
}
