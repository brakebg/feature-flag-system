package com.example.featureflags;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.security.core.SpringSecurityCoreVersion;

/** AC-UPG-1 (spec 002 section 3): versions are read from the libraries at runtime. */
@Tag("AC-UPG-1")
class PlatformVersionsTest {

  @Test
  void springBootIs41() {
    assertThat(SpringBootVersion.getVersion()).startsWith("4.1.");
  }

  @Test
  void springFrameworkIs709OrLater() {
    String v = SpringVersion.getVersion();
    assertThat(v).startsWith("7.");
    assertThat(patchOf(v, "7.0.")).isGreaterThanOrEqualTo(9);
  }

  @Test
  void springSecurityIs71() {
    assertThat(SpringSecurityCoreVersion.getVersion()).startsWith("7.1.");
  }

  @Test
  void jacksonIs3() {
    assertThat(tools.jackson.databind.cfg.PackageVersion.VERSION.getMajorVersion()).isEqualTo(3);
    assertThat(tools.jackson.core.json.PackageVersion.VERSION.getMajorVersion()).isEqualTo(3);
  }

  /** Patch number of {@code 7.0.x}; a later minor line (7.1 and up) counts as new enough. */
  private static int patchOf(String version, String prefix) {
    if (!version.startsWith(prefix)) {
      return Integer.MAX_VALUE;
    }
    String patch = version.substring(prefix.length()).split("[^0-9]")[0];
    return Integer.parseInt(patch);
  }
}
