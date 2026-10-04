package com.example.featureflags.audit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Spec 6.1 {@code GET /audit}. */
@RestController
public class AuditController {

  private final AuditService audit;

  public AuditController(AuditService audit) {
    this.audit = audit;
  }

  @GetMapping("/api/v1/admin/audit")
  public PagedModel<AuditEventView> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
      @RequestParam(required = false) String targetKey) {
    return new PagedModel<>(audit.page(page, size, targetKey));
  }
}
