package com.example.featureflags.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Spec 5.1: credential checks behind interfaces, compared in constant time. */
class AuthenticatorsTest {

  private final ClientRegistrationProperties props = TokenIssuerTest.props();

  @Test
  void adminAuthenticatorAcceptsOnlyTheConfiguredPair() {
    AdminAuthenticator admin = new ConfigAdminAuthenticator(props);
    assertThat(admin.authenticate("admin", "admin123")).isTrue();
    assertThat(admin.authenticate("admin", "admin124")).isFalse();
    assertThat(admin.authenticate("root", "admin123")).isFalse();
    assertThat(admin.authenticate("admin", "")).isFalse();
  }

  @Test
  void clientAuthenticatorReturnsTheRegistration() {
    ClientAuthenticator clients = new ConfigClientAuthenticator(props);
    assertThat(clients.authenticate("order-service", "s"))
        .hasValueSatisfying(c -> assertThat(c.clientId()).isEqualTo("order-service"));
    assertThat(clients.authenticate("order-service", "wrong")).isEmpty();
    assertThat(clients.authenticate("unknown", "s")).isEmpty();
  }
}
