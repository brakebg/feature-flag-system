package com.example.featureflags.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Spec 4.1: created_by / updated_by come from the authenticated username (JWT sub). */
class SecurityAuditorTest {

  private final SecurityAuditor auditor = new SecurityAuditor();

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void usesTheAuthenticatedName() {
    TestingAuthenticationToken auth = new TestingAuthenticationToken("admin", null, "SCOPE_admin");
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(auditor.getCurrentAuditor()).contains("admin");
  }

  @Test
  void emptyWithoutAuthenticationOrForAnonymous() {
    assertThat(auditor.getCurrentAuditor()).isEmpty();

    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "k", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    assertThat(auditor.getCurrentAuditor()).isEmpty();
  }

  @Test
  void emptyWhenNotAuthenticated() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("admin", null));
    assertThat(auditor.getCurrentAuditor()).isEmpty();
  }
}
