package com.example.featureflags.audit;

import com.example.featureflags.common.FieldValidationException;
import com.example.featureflags.common.MalformedRequestException;
import java.math.BigInteger;
import java.util.regex.Pattern;
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

  private static final Pattern WHOLE_NUMBER = Pattern.compile("[+-]?\\d+");
  private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
  private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

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
    if (targetKey != null && targetKey.indexOf('\u0000') >= 0) {
      // FR-8: PostgreSQL text cannot hold U+0000; invalid input (6.1 400), not a 500.
      throw new FieldValidationException("targetKey", "must not contain the character U+0000");
    }
    return new PagedModel<>(audit.page(p, s, targetKey));
  }

  /**
   * Spec 6.1: a non-numeric value is malformed; any whole number is numeric, also beyond the int
   * range (BF-2). Values are clamped to the int range: a clamped page is past the end (empty page),
   * a clamped size or a negative page fails the range check (400 validation).
   */
  private static int number(String name, String value, int fallback) {
    if (value == null) {
      return fallback;
    }
    if (!WHOLE_NUMBER.matcher(value).matches()) {
      throw new MalformedRequestException(name + " must be a whole number");
    }
    BigInteger n = new BigInteger(value);
    return n.max(INT_MIN).min(INT_MAX).intValueExact();
  }
}
