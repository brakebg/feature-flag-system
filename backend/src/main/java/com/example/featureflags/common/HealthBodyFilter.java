package com.example.featureflags.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
  private final ObjectMapper mapper;

  public HealthBodyFilter(ObjectMapper mapper) {
    this.mapper = mapper;
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
    JsonNode body = mapper.readTree(wrapper.getContentAsByteArray());
    if (body == null || !body.has("status")) {
      wrapper.copyBodyToResponse();
      return;
    }
    ObjectNode out = mapper.createObjectNode();
    out.set("status", body.get("status"));
    JsonNode db = body.path("components").path("db");
    if (!db.isMissingNode()) {
      out.putObject("components").set("db", db);
    }
    byte[] bytes = mapper.writeValueAsBytes(out);
    wrapper.resetBuffer();
    wrapper.getOutputStream().write(bytes);
    wrapper.setContentLength(bytes.length);
    wrapper.copyBodyToResponse();
  }
}
