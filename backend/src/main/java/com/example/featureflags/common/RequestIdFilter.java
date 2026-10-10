package com.example.featureflags.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spec 9.3: every response carries {@code X-Request-Id} (the client value unchanged, or a new
 * UUID), and every request is logged with method, path, status, duration and that id. Runs first,
 * so security errors carry the id too. The query string is never logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

  /** Set by the Evaluation API to the consumer's client id (JWT sub), logged per request. */
  public static final String CLIENT_ATTRIBUTE = "ff.client";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = request.getHeader(RequestIds.HEADER);
    if (id == null || id.isEmpty()) {
      id = UUID.randomUUID().toString();
    }
    response.setHeader(RequestIds.HEADER, id);
    MDC.put(RequestIds.MDC_KEY, id);
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long ms = (System.nanoTime() - start) / 1_000_000;
      Object client = request.getAttribute(CLIENT_ATTRIBUTE);
      log.info(
          "{} {} {} {} ms requestId={}{}",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          ms,
          id,
          client == null ? "" : " client=" + client);
      MDC.remove(RequestIds.MDC_KEY);
    }
  }
}
