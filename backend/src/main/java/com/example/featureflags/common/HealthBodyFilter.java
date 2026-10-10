package com.example.featureflags.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Spec 9.3: {@code GET /actuator/health} returns exactly {@code
 * {"status":"UP","components":{"db":{"status":"UP"}}}}. Spring Boot also lists the probe groups and
 * the liveness / readiness state contributors in the root body (they back {@code
 * /actuator/health/liveness} and {@code /readiness}); this filter keeps only {@code status} and the
 * {@code db} component in the root response. The status code is not changed.
 */
@Component
public class HealthBodyFilter extends OncePerRequestFilter {

  static final String PATH = "/actuator/health";
  private final Json json;

  public HealthBodyFilter(Json json) {
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !("GET".equals(request.getMethod()) && PATH.equals(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
    chain.doFilter(request, wrapper);
    byte[] bytes = json.keepHealthStatusAndDb(wrapper.getContentAsByteArray());
    if (bytes == null) {
      wrapper.copyBodyToResponse();
      return;
    }
    wrapper.resetBuffer();
    wrapper.getOutputStream().write(bytes);
    wrapper.setContentLength(bytes.length);
    wrapper.copyBodyToResponse();
  }
}
