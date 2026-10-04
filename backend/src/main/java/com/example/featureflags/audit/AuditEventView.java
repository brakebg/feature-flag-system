package com.example.featureflags.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/** Spec 6.2 {@code AuditEvent} (the response shape; the entity is {@link AuditEvent}). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEventView(
    long id,
    Instant occurredAt,
    String actor,
    String action,
    String targetKey,
    Map<String, Object> details) {

  static AuditEventView of(AuditEvent e) {
    return new AuditEventView(
        e.getId(),
        e.getOccurredAt(),
        e.getActor(),
        e.getAction().name(),
        e.getTargetKey(),
        e.getDetails());
  }
}
