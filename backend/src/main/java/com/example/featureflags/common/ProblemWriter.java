package com.example.featureflags.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/** Writes a spec 9.1 problem detail from a servlet filter (outside Spring MVC). */
@Component
public class ProblemWriter {

  private final Json json;

  public ProblemWriter(Json json) {
    this.json = json;
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      String type,
      String title,
      String detail)
      throws IOException {
    ProblemDetail p = Problems.of(status, type, title, detail, request);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.write(response.getOutputStream(), p);
  }
}
