package com.example.featureflags.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Spec 5.1: auth configuration, defaults and fail-fast rules. */
class AuthPropertiesTest {

  @Configuration
  @EnableConfigurationProperties(ClientRegistrationProperties.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(Config.class)
          .withInitializer(
              ctx -> new ClientEnvironment().postProcessEnvironment(ctx.getEnvironment(), null))
          .withPropertyValues(
              "featureflags.auth.admin-username=admin",
              "featureflags.auth.admin-password=admin123",
              "featureflags.auth.jwt-secret=change-me-to-a-32-byte-minimum-secret!!",
              "featureflags.auth.issuer=feature-flag-service",
              "featureflags.auth.admin-token-ttl=PT8H",
              "featureflags.auth.client-token-ttl=PT15M",
              "featureflags.auth.clients[0].client-id=order-service",
              "featureflags.auth.clients[0].client-secret=order-service-dev-secret",
              "featureflags.auth.clients[0].scopes[0]=flags:read");

  @Test
  void bindsTheSpecConfiguration() {
    runner.run(
        ctx -> {
          ClientRegistrationProperties p = ctx.getBean(ClientRegistrationProperties.class);
          assertThat(p.adminTokenTtl()).isEqualTo(Duration.ofHours(8));
          assertThat(p.clientTokenTtl()).isEqualTo(Duration.ofMinutes(15));
          assertThat(p.clients())
              .containsExactly(
                  new ClientRegistration(
                      "order-service", "order-service-dev-secret", List.of("flags:read")));
        });
  }

  @Test
  void secretShorterThan32BytesFailsStartup() {
    runner
        .withPropertyValues("featureflags.auth.jwt-secret=" + "x".repeat(31))
        .run(ctx -> failedWith(ctx, "jwt-secret"));
    runner
        .withPropertyValues("featureflags.auth.jwt-secret=" + "x".repeat(32))
        .run(ctx -> assertThat(ctx).hasNotFailed());
  }

  @Test
  void secretLengthCountsUtf8Bytes() {
    // 16 x "é" = 16 characters but 32 UTF-8 bytes
    runner
        .withPropertyValues("featureflags.auth.jwt-secret=" + "é".repeat(16))
        .run(ctx -> assertThat(ctx).hasNotFailed());
    runner
        .withPropertyValues("featureflags.auth.jwt-secret=" + "é".repeat(15) + "x")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void duplicateClientIdFailsStartup() {
    runner
        .withPropertyValues(
            "featureflags.auth.clients[1].client-id=order-service",
            "featureflags.auth.clients[1].client-secret=other",
            "featureflags.auth.clients[1].scopes[0]=flags:read")
        .run(ctx -> failedWith(ctx, "client-id"));
  }

  private static void failedWith(
      org.springframework.boot.test.context.assertj.AssertableApplicationContext ctx, String text) {
    assertThat(ctx).hasFailed();
    assertThat(
            org.springframework.core.NestedExceptionUtils.getMostSpecificCause(
                ctx.getStartupFailure()))
        .hasMessageContaining(text);
  }

  @Test
  void ttlBelowOneSecondFailsAndFractionsAreAccepted() {
    // ESC-007 A (5.1): any duration of 1 second or more is accepted; fractions are allowed.
    runner
        .withPropertyValues("featureflags.auth.admin-token-ttl=PT0.5S")
        .run(ctx -> failedWith(ctx, "admin-token-ttl"));
    runner
        .withPropertyValues("featureflags.auth.client-token-ttl=PT0.999S")
        .run(ctx -> failedWith(ctx, "client-token-ttl"));
    runner
        .withPropertyValues(
            "featureflags.auth.client-token-ttl=PT1.5S", "featureflags.auth.admin-token-ttl=PT1S")
        .run(ctx -> assertThat(ctx).hasNotFailed());
  }

  @Test
  void blankClientSecretOrAdminPasswordFails() {
    runner
        .withPropertyValues("featureflags.auth.clients[0].client-secret=")
        .run(ctx -> failedWith(ctx, "client-secret"));
    runner
        .withPropertyValues("featureflags.auth.admin-password=")
        .run(ctx -> failedWith(ctx, "admin-password"));
  }

  @Test
  void secretIsNeverInToString() {
    assertThat(new ClientRegistration("a", "top-secret", List.of("flags:read")).toString())
        .doesNotContain("top-secret");
  }

  @Test
  void envClientsAreAddedAfterTheConfiguredClients() {
    // ESC-004 item 2 (5.1): env clients are appended; order-service from the config stays.
    runner
        .withSystemProperties(
            "FF_AUTH_CLIENTS_0_CLIENT_ID=billing",
            "FF_AUTH_CLIENTS_0_CLIENT_SECRET=billing-secret",
            "FF_AUTH_CLIENTS_0_SCOPES=flags:read, other",
            "FF_AUTH_CLIENTS_1_CLIENT_ID=shipping",
            "FF_AUTH_CLIENTS_1_CLIENT_SECRET=shipping-secret",
            "FF_AUTH_CLIENTS_1_SCOPES=flags:read")
        .run(
            ctx ->
                assertThat(ctx.getBean(ClientRegistrationProperties.class).clients())
                    .containsExactly(
                        new ClientRegistration(
                            "order-service", "order-service-dev-secret", List.of("flags:read")),
                        new ClientRegistration(
                            "billing", "billing-secret", List.of("flags:read", "other")),
                        new ClientRegistration(
                            "shipping", "shipping-secret", List.of("flags:read"))));
  }

  @Test
  void envClientsWithoutConfiguredClientsStartAtIndexZero() {
    new ApplicationContextRunner()
        .withUserConfiguration(Config.class)
        .withInitializer(
            ctx -> new ClientEnvironment().postProcessEnvironment(ctx.getEnvironment(), null))
        .withPropertyValues(
            "featureflags.auth.admin-password=admin123",
            "featureflags.auth.jwt-secret=change-me-to-a-32-byte-minimum-secret!!",
            "featureflags.auth.admin-token-ttl=PT8H",
            "featureflags.auth.client-token-ttl=PT15M")
        .withSystemProperties(
            "FF_AUTH_CLIENTS_0_CLIENT_ID=billing",
            "FF_AUTH_CLIENTS_0_CLIENT_SECRET=billing-secret",
            "FF_AUTH_CLIENTS_0_SCOPES=flags:read")
        .run(
            ctx ->
                assertThat(ctx.getBean(ClientRegistrationProperties.class).clients())
                    .containsExactly(
                        new ClientRegistration(
                            "billing", "billing-secret", List.of("flags:read"))));
  }

  @Test
  void envClientWithAConfiguredClientIdFailsStartup() {
    // ESC-004 item 2: the fail-fast rules apply to the merged list.
    runner
        .withSystemProperties(
            "FF_AUTH_CLIENTS_0_CLIENT_ID=order-service",
            "FF_AUTH_CLIENTS_0_CLIENT_SECRET=other-secret",
            "FF_AUTH_CLIENTS_0_SCOPES=flags:read")
        .run(ctx -> failedWith(ctx, "client-id"));
  }

  @Test
  void envClientWithABlankSecretFailsStartup() {
    runner
        .withSystemProperties(
            "FF_AUTH_CLIENTS_0_CLIENT_ID=billing",
            "FF_AUTH_CLIENTS_0_CLIENT_SECRET= ",
            "FF_AUTH_CLIENTS_0_SCOPES=flags:read")
        .run(ctx -> failedWith(ctx, "client-secret"));
  }
}
