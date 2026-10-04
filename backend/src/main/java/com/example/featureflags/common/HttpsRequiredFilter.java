package com.example.featureflags.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spec 10.2: with {@code FF_REQUIRE_HTTPS=true}, {@code POST /api/v1/auth/login} and {@code /token}
 * that did not arrive over HTTPS get 403 {@code https-required}. {@code X-Forwarded-Proto} is
 * applied before this filter ({@code forward-headers-strategy=framework}), so a request without it,
 * or with {@code http}, is not secure.
 */
public class HttpsRequiredFilter extends OncePerRequestFilter {

  private final boolean required;
  private final ProblemWriter problems;

  public HttpsRequiredFilter(boolean required, ProblemWriter problems) {
    this.required = required;
    this.problems = problems;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = RequestPaths.of(request);
    return !required
        || !"POST".equals(request.getMethod())
        || !(path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/token"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.isSecure()) {
      chain.doFilter(request, response);
      return;
    }
    problems.write(
        request,
        response,
        HttpStatus.FORBIDDEN,
        "https-required",
        "HTTPS required",
        "Credentials are accepted only over HTTPS");
  }
}
