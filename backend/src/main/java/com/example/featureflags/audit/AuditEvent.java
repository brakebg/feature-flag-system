package com.example.featureflags.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Spec 4.1 {@code audit_event}: append-only, no foreign keys. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "actor", nullable = false, updatable = false, length = 100)
  private String actor;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, updatable = false, length = 30)
  private AuditAction action;

  @Column(name = "target_key", nullable = false, updatable = false, length = 101)
  private String targetKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "details", updatable = false, columnDefinition = "jsonb")
  private Map<String, Object> details;

  protected AuditEvent() {}

  public AuditEvent(
      Instant occurredAt,
      String actor,
      AuditAction action,
      String targetKey,
      Map<String, Object> details) {
    this.occurredAt = occurredAt;
    this.actor = actor;
    this.action = action;
    this.targetKey = targetKey;
    this.details = details;
  }

  public Long getId() {
    return id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getActor() {
    return actor;
  }

  public AuditAction getAction() {
    return action;
  }

  public String getTargetKey() {
    return targetKey;
  }

  public Map<String, Object> getDetails() {
    return details;
  }
}
