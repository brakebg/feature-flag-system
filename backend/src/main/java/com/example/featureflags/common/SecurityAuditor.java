package com.example.featureflags.common;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Spec 4.1: the auditor is the authenticated username (the JWT {@code sub}). */
@Component("securityAuditor")
public class SecurityAuditor implements AuditorAware<String> {

  @Override
  public Optional<String> getCurrentAuditor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
      return Optional.empty();
    }
    return Optional.ofNullable(auth.getName());
  }
}
