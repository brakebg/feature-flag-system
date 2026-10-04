package com.example.featureflags.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** Builds RFC 9457 problem details in the one shape of spec 9.1. */
public final class Problems {

  public static final String TYPE_BASE = "https://featureflags.local/problems/";

  private Problems() {}

  public static ProblemDetail of(HttpStatus status, String type, String title, String detail) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setType(URI.create(TYPE_BASE + type));
    p.setTitle(title);
    return p;
  }

  /** A problem with the request path (without query) as {@code instance}. */
  public static ProblemDetail of(
      HttpStatus status, String type, String title, String detail, HttpServletRequest request) {
    ProblemDetail p = of(status, type, title, detail);
    p.setInstance(URI.create(request.getRequestURI()));
    return p;
  }

  public static ProblemDetail validation(
      List<Map<String, String>> errors, HttpServletRequest request) {
    String detail =
        "Request has " + errors.size() + " invalid field" + (errors.size() == 1 ? "" : "s");
    ProblemDetail p =
        of(HttpStatus.BAD_REQUEST, "validation", "Validation failed", detail, request);
    p.setProperty("errors", errors);
    return p;
  }

  public static ResponseEntity<ProblemDetail> response(ProblemDetail p) {
    return ResponseEntity.status(p.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(p);
  }
}
