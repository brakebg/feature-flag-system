package com.example.featureflags.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Spec 5.1: prod WARN for dev defaults, without logging the values. */
@ExtendWith(OutputCaptureExtension.class)
class DefaultSecretsWarningTest {

  @Test
  void warnsForEachDefaultWithoutTheValue(CapturedOutput output) {
    new DefaultSecretsWarning(TokenIssuerTest.props()).warn();
    // TokenIssuerTest.props(): dev admin password and dev JWT secret, client secret "s"
    assertThat(output.getOut())
        .contains("WARN")
        .contains("FF_ADMIN_PASSWORD")
        .contains("FF_JWT_SECRET")
        .doesNotContain("admin123")
        .doesNotContain("change-me-to-a-32-byte-minimum-secret!!");
  }

  @Test
  void silentWhenNoDefaultIsUsed(CapturedOutput output) {
    ClientRegistrationProperties real =
        new ClientRegistrationProperties(
            "admin",
            "a-real-password",
            "a-real-secret-with-at-least-32-bytes!!",
            "feature-flag-service",
            Duration.ofHours(8),
            Duration.ofMinutes(15),
            List.of(new ClientRegistration("order-service", "real", List.of("flags:read"))));
    new DefaultSecretsWarning(real).warn();
    assertThat(output.getOut()).doesNotContain("Default dev credential");
  }

  @Test
  void namesTheOrderServiceDefault() {
    ClientRegistrationProperties p =
        new ClientRegistrationProperties(
            "admin",
            "x",
            "a-real-secret-with-at-least-32-bytes!!",
            "feature-flag-service",
            Duration.ofHours(8),
            Duration.ofMinutes(15),
            List.of(
                new ClientRegistration(
                    "order-service", "order-service-dev-secret", List.of("flags:read"))));
    assertThat(DefaultSecretsWarning.defaultsInUse(p))
        .containsExactly("secret of client order-service");
  }
}
