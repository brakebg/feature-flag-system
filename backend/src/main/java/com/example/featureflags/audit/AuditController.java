package com.example.featureflags.audit;

import com.example.featureflags.common.FieldValidationException;
import com.example.featureflags.common.MalformedRequestException;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spec 6.1 {@code GET /audit}: {@code page} 0 or more, {@code size} 1 to 200 (default 50),
 * otherwise 400 {@code validation}; a value that is not a whole number (also an empty value) is 400
 * {@code malformed-request}.
 */
@RestController
public class AuditController {

  static final int DEFAULT_SIZE = 50;
  static final int MAX_SIZE = 200;

  private final AuditService audit;

  public AuditController(AuditService audit) {
    this.audit = audit;
  }

  @GetMapping("/api/v1/admin/audit")
  public PagedModel<AuditEventView> list(
      @RequestParam(required = false) String page,
      @RequestParam(required = false) String size,
      @RequestParam(required = false) String targetKey) {
    int p = number("page", page, 0);
    int s = number("size", size, DEFAULT_SIZE);
    if (p < 0) {
      throw new FieldValidationException("page", "must be 0 or more");
    }
    if (s < 1 || s > MAX_SIZE) {
      throw new FieldValidationException("size", "must be between 1 and " + MAX_SIZE);
    }
    return new PagedModel<>(audit.page(p, s, targetKey));
  }

  private static int number(String name, String value, int fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new MalformedRequestException(name + " must be a whole number");
    }
  }
}
