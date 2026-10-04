package com.example.featureflags.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * The decoded path that Spring Security and Spring MVC match on. Filters must use it instead of the
 * raw {@code getRequestURI()}, or a percent-encoded path ({@code /api/v1/auth/%6cogin}) would slip
 * past them while still reaching the controller.
 */
public final class RequestPaths {

  private static final UrlPathHelper HELPER = new UrlPathHelper();

  static {
    HELPER.setUrlDecode(true);
    HELPER.setRemoveSemicolonContent(true);
  }

  private RequestPaths() {}

  public static String of(HttpServletRequest request) {
    return HELPER.getPathWithinApplication(request);
  }
}
